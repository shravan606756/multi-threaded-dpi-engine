#include "load_balancer.h"
#include <iostream>
#include <chrono>
#include <stdexcept>

namespace DPI {

// ============================================================================
// LoadBalancer Implementation
// ============================================================================

LoadBalancer::LoadBalancer(int lb_id,
                           std::vector<ThreadSafeQueue<PacketJob>*> fp_queues,
                           int fp_start_id)
    : lb_id_(lb_id),
      fp_start_id_(fp_start_id),
      num_fps_(fp_queues.size()),
      input_queue_(10000),
      fp_queues_(std::move(fp_queues)),
      per_fp_counts_(static_cast<size_t>(num_fps_)) {
    std::cerr << "[LB" << lb_id_ << "][DEBUG] Constructed with "
              << num_fps_ << " FP queues\n";
}

LoadBalancer::~LoadBalancer() {
    stop();
}

void LoadBalancer::start() {
    if (running_) return;
    
    running_ = true;
    thread_ = std::thread(&LoadBalancer::run, this);
    
    std::cout << "[LB" << lb_id_ << "] Started (serving FP" 
              << fp_start_id_ << "-FP" << (fp_start_id_ + num_fps_ - 1) << ")\n";
}

void LoadBalancer::stop() {
    if (!running_) return;
    
    running_ = false;
    input_queue_.shutdown();
    
    if (thread_.joinable()) {
        thread_.join();
    }
    
    std::cout << "[LB" << lb_id_ << "] Stopped\n";
}

void LoadBalancer::run() {
    bool logged_select_fp_invalid = false;
    bool logged_fp_index_oob = false;
    bool logged_null_target_queue = false;

    while (running_) {
        // Get packet from input queue (with timeout to check running flag)
        auto job_opt = input_queue_.popWithTimeout(std::chrono::milliseconds(100));
        
        if (!job_opt) {
            continue;  // Timeout or shutdown
        }
        
        packets_received_++;
        
        // Select target FP based on five-tuple hash
        int fp_index = selectFP(job_opt->tuple);
        if (fp_index < 0) {
            if (!logged_select_fp_invalid) {
                std::cerr << "[LB" << lb_id_
                          << "][DEBUG] selectFP returned -1 (num_fps_=" << num_fps_ << ")\n";
                logged_select_fp_invalid = true;
            }
            continue;
        }
        const size_t idx = static_cast<size_t>(fp_index);
        if (idx >= fp_queues_.size() || idx >= per_fp_counts_.size()) {
            if (!logged_fp_index_oob) {
                std::cerr << "[LB" << lb_id_ << "][DEBUG] FP index out of range: idx="
                          << idx << " fp_queues_.size=" << fp_queues_.size()
                          << " per_fp_counts_.size=" << per_fp_counts_.size() << "\n";
                logged_fp_index_oob = true;
            }
            continue;
        }
        ThreadSafeQueue<PacketJob>* target_queue = fp_queues_.at(idx);
        if (!target_queue) {
            if (!logged_null_target_queue) {
                std::cerr << "[LB" << lb_id_ << "][DEBUG] Null target FP queue at idx=" << idx << "\n";
                logged_null_target_queue = true;
            }
            continue;
        }
        
        // Push to selected FP's queue
        target_queue->push(std::move(*job_opt));
        
        packets_dispatched_++;
        {
            std::lock_guard<std::mutex> lock(per_fp_counts_mutex_);
            per_fp_counts_.at(idx)++;
        }
    }
}

int LoadBalancer::selectFP(const FiveTuple& tuple) {
    if (num_fps_ <= 0) {
        return -1;
    }
    // Hash the five-tuple and map to one of our FPs
    FiveTupleHash hasher;
    size_t hash = hasher(tuple);
    return hash % num_fps_;
}

LoadBalancer::LBStats LoadBalancer::getStats() const {
    LBStats stats;
    stats.packets_received = packets_received_.load();
    stats.packets_dispatched = packets_dispatched_.load();

    {
        std::lock_guard<std::mutex> lock(per_fp_counts_mutex_);
        stats.per_fp_packets = per_fp_counts_;
    }
    
    return stats;
}

// ============================================================================
// LBManager Implementation
// ============================================================================

LBManager::LBManager(int num_lbs, int fps_per_lb,
                     std::vector<ThreadSafeQueue<PacketJob>*> fp_queues)
    : fps_per_lb_(fps_per_lb) {
    if (num_lbs <= 0) {
        throw std::runtime_error("LBManager: num_lbs must be > 0");
    }
    if (fps_per_lb <= 0) {
        throw std::runtime_error("LBManager: fps_per_lb must be > 0");
    }
    const size_t required_fp_queues =
        static_cast<size_t>(num_lbs) * static_cast<size_t>(fps_per_lb);
    if (fp_queues.size() < required_fp_queues) {
        throw std::runtime_error("LBManager: insufficient FP queues for configured topology");
    }
    
    // Create load balancers, each handling a subset of FPs
    for (int lb_id = 0; lb_id < num_lbs; lb_id++) {
        std::vector<ThreadSafeQueue<PacketJob>*> lb_fp_queues;
        lb_fp_queues.reserve(static_cast<size_t>(fps_per_lb));
        int fp_start = lb_id * fps_per_lb;
        
        for (int i = 0; i < fps_per_lb; i++) {
            const int fp_index = fp_start + i;
            if (fp_index < 0) {
                throw std::runtime_error("LBManager: negative FP index while building topology");
            }
            const size_t idx = static_cast<size_t>(fp_index);
            if (idx >= fp_queues.size()) {
                throw std::runtime_error("LBManager: FP index out of range while building topology");
            }
            ThreadSafeQueue<PacketJob>* queue = fp_queues.at(idx);
            if (!queue) {
                throw std::runtime_error("LBManager: null FP queue pointer in topology");
            }
            lb_fp_queues.push_back(queue);
        }

        if (lb_fp_queues.size() != static_cast<size_t>(fps_per_lb)) {
            throw std::runtime_error("LBManager: incomplete FP queue set for load balancer");
        }

        std::cerr << "[LBManager][DEBUG] LB" << lb_id
                  << " topology: fp_start=" << fp_start
                  << " queue_count=" << lb_fp_queues.size()
                  << " expected=" << fps_per_lb << "\n";
        
        lbs_.push_back(std::make_unique<LoadBalancer>(lb_id, lb_fp_queues, fp_start));
    }
    
    std::cout << "[LBManager] Created " << num_lbs << " load balancers, "
              << fps_per_lb << " FPs each\n";
}

LBManager::~LBManager() {
    stopAll();
}

void LBManager::startAll() {
    for (auto& lb : lbs_) {
        lb->start();
    }
}

void LBManager::stopAll() {
    for (auto& lb : lbs_) {
        lb->stop();
    }
}

LoadBalancer& LBManager::getLBForPacket(const FiveTuple& tuple) {
    if (lbs_.empty()) {
        throw std::runtime_error("LBManager has no load balancers");
    }
    // First level of load balancing: select LB based on hash
    FiveTupleHash hasher;
    size_t hash = hasher(tuple);
    size_t lb_index = hash % lbs_.size();
    return *lbs_.at(lb_index);
}

LBManager::AggregatedStats LBManager::getAggregatedStats() const {
    AggregatedStats stats = {0, 0};
    
    for (const auto& lb : lbs_) {
        auto lb_stats = lb->getStats();
        stats.total_received += lb_stats.packets_received;
        stats.total_dispatched += lb_stats.packets_dispatched;
    }
    
    return stats;
}

bool LBManager::queuesEmpty() const {
    for (const auto& lb : lbs_) {
        if (!lb->getInputQueue().empty()) {
            return false;
        }
    }
    return true;
}

} // namespace DPI
