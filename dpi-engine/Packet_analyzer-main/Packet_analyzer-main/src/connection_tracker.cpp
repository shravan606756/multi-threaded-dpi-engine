#include "connection_tracker.h"
#include <algorithm>
#include <sstream>
#include <iomanip>
#include <mutex>
#include <iostream>

namespace DPI {
namespace {

std::string formatIP(uint32_t ip) {
    std::ostringstream ss;
    ss << ((ip >> 24) & 0xFF) << "."
       << ((ip >> 16) & 0xFF) << "."
       << ((ip >> 8) & 0xFF) << "."
       << (ip & 0xFF);
    return ss.str();
}

uint64_t nowEpochSeconds() {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()).count());
}

void emitFlowTelemetry(const Connection& conn) {
    const uint64_t packet_count = conn.packets_in + conn.packets_out;
    const uint64_t total_bytes = conn.bytes_in + conn.bytes_out;
    const uint64_t avg_packet_size = packet_count > 0 ? (total_bytes / packet_count) : 0;

    uint64_t flow_duration = 0;
    if (conn.last_epoch_seconds >= conn.first_epoch_seconds) {
        flow_duration = conn.last_epoch_seconds - conn.first_epoch_seconds;
    }

    static std::mutex json_out_mutex;
    std::lock_guard<std::mutex> lock(json_out_mutex);
    std::cout
        << "{"
        << "\"timestamp\":" << conn.first_epoch_seconds << ","
        << "\"src_ip\":\"" << formatIP(conn.tuple.src_ip) << "\","
        << "\"dst_ip\":\"" << formatIP(conn.tuple.dst_ip) << "\","
        << "\"protocol\":\"" << appTypeToString(conn.app_type) << "\","
        << "\"sni\":\"" << conn.sni << "\"," 
        << "\"packet_count\":" << packet_count << ","
        << "\"avg_packet_size\":" << avg_packet_size << ","
        << "\"flow_duration\":" << flow_duration
        << "}"
        << std::endl;
}

}  // namespace

// ============================================================================
// ConnectionTracker Implementation
// ============================================================================

ConnectionTracker::ConnectionTracker(int fp_id, size_t max_connections)
    : fp_id_(fp_id), max_connections_(max_connections) {
}

Connection* ConnectionTracker::getOrCreateConnection(const FiveTuple& tuple) {
    auto it = connections_.find(tuple);
    
    if (it != connections_.end()) {
        return &it->second;
    }
    
    // Check if we need to evict old connections
    if (connections_.size() >= max_connections_) {
        evictOldest();
    }
    
    // Create new connection
    Connection conn;
    conn.tuple = tuple;
    conn.state = ConnectionState::NEW;
    conn.first_seen = std::chrono::steady_clock::now();
    conn.last_seen = conn.first_seen;
    conn.first_epoch_seconds = nowEpochSeconds();
    conn.last_epoch_seconds = conn.first_epoch_seconds;
    
    auto result = connections_.emplace(tuple, std::move(conn));
    total_seen_++;
    
    return &result.first->second;
}

Connection* ConnectionTracker::getConnection(const FiveTuple& tuple) {
    auto it = connections_.find(tuple);
    if (it != connections_.end()) {
        return &it->second;
    }
    
    // Try reverse tuple (for bidirectional matching)
    auto rev = connections_.find(tuple.reverse());
    if (rev != connections_.end()) {
        return &rev->second;
    }
    
    return nullptr;
}

void ConnectionTracker::updateConnection(Connection* conn, size_t packet_size, bool is_outbound) {
    if (!conn) return;
    
    conn->last_seen = std::chrono::steady_clock::now();
    conn->last_epoch_seconds = nowEpochSeconds();
    
    if (is_outbound) {
        conn->packets_out++;
        conn->bytes_out += packet_size;
    } else {
        conn->packets_in++;
        conn->bytes_in += packet_size;
    }
}

void ConnectionTracker::classifyConnection(Connection* conn, AppType app, const std::string& sni) {
    if (!conn) return;
    
    if (conn->state != ConnectionState::CLASSIFIED) {
        conn->app_type = app;
        conn->sni = sni;
        conn->state = ConnectionState::CLASSIFIED;
        classified_count_++;
    }
}

void ConnectionTracker::blockConnection(Connection* conn) {
    if (!conn) return;
    
    conn->state = ConnectionState::BLOCKED;
    conn->action = PacketAction::DROP;
    blocked_count_++;
}

void ConnectionTracker::closeConnection(const FiveTuple& tuple) {
    auto it = connections_.find(tuple);
    if (it != connections_.end()) {
        it->second.state = ConnectionState::CLOSED;
    }
}

size_t ConnectionTracker::cleanupStale(std::chrono::seconds timeout) {
    auto now = std::chrono::steady_clock::now();
    size_t removed = 0;
    
    for (auto it = connections_.begin(); it != connections_.end(); ) {
        auto age = std::chrono::duration_cast<std::chrono::seconds>(
            now - it->second.last_seen);
        
        if (age > timeout || it->second.state == ConnectionState::CLOSED) {
            emitFlowTelemetry(it->second);
            it = connections_.erase(it);
            removed++;
        } else {
            ++it;
        }
    }
    
    return removed;
}

std::vector<Connection> ConnectionTracker::getAllConnections() const {
    std::vector<Connection> result;
    result.reserve(connections_.size());
    
    for (const auto& pair : connections_) {
        result.push_back(pair.second);
    }
    
    return result;
}

size_t ConnectionTracker::getActiveCount() const {
    return connections_.size();
}

ConnectionTracker::TrackerStats ConnectionTracker::getStats() const {
    TrackerStats stats;
    stats.active_connections = connections_.size();
    stats.total_connections_seen = total_seen_;
    stats.classified_connections = classified_count_;
    stats.blocked_connections = blocked_count_;
    return stats;
}

void ConnectionTracker::clear() {
    connections_.clear();
}

void ConnectionTracker::forEach(std::function<void(const Connection&)> callback) const {
    for (const auto& pair : connections_) {
        callback(pair.second);
    }
}

void ConnectionTracker::evictOldest() {
    if (connections_.empty()) return;
    
    // Find oldest connection
    auto oldest = connections_.begin();
    for (auto it = connections_.begin(); it != connections_.end(); ++it) {
        if (it->second.last_seen < oldest->second.last_seen) {
            oldest = it;
        }
    }
    
    emitFlowTelemetry(oldest->second);
    connections_.erase(oldest);
}

// ============================================================================
// GlobalConnectionTable Implementation
// ============================================================================

GlobalConnectionTable::GlobalConnectionTable(size_t num_fps) {
    trackers_.resize(num_fps, nullptr);
}

void GlobalConnectionTable::registerTracker(int fp_id, ConnectionTracker* tracker) {
    std::unique_lock<std::shared_mutex> lock(mutex_);
    if (fp_id < static_cast<int>(trackers_.size())) {
        trackers_[fp_id] = tracker;
    }
}

GlobalConnectionTable::GlobalStats GlobalConnectionTable::getGlobalStats() const {
    std::shared_lock<std::shared_mutex> lock(mutex_);
    
    GlobalStats stats;
    stats.total_active_connections = 0;
    stats.total_connections_seen = 0;
    
    std::unordered_map<std::string, size_t> domain_counts;
    
    for (const auto* tracker : trackers_) {
        if (!tracker) continue;
        
        auto tracker_stats = tracker->getStats();
        stats.total_active_connections += tracker_stats.active_connections;
        stats.total_connections_seen += tracker_stats.total_connections_seen;
        
        // Collect app distribution
        tracker->forEach([&](const Connection& conn) {
            stats.app_distribution[conn.app_type]++;
            if (!conn.sni.empty()) {
                domain_counts[conn.sni]++;
            }
        });
    }
    
    // Get top domains
    std::vector<std::pair<std::string, size_t>> domain_vec(
        domain_counts.begin(), domain_counts.end());
    
    std::sort(domain_vec.begin(), domain_vec.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });
    
    // Take top 20
    size_t count = std::min(domain_vec.size(), static_cast<size_t>(20));
    stats.top_domains.assign(domain_vec.begin(), domain_vec.begin() + count);
    
    return stats;
}

std::string GlobalConnectionTable::generateReport() const {
    auto stats = getGlobalStats();

    std::ostringstream ss;
    ss << "\n[ConnectionTracker] Statistics\n";
    ss << "  active_connections=" << stats.total_active_connections << "\n";
    ss << "  total_connections_seen=" << stats.total_connections_seen << "\n";

    size_t total = 0;
    for (const auto& pair : stats.app_distribution) {
        total += pair.second;
    }

    std::vector<std::pair<AppType, size_t>> sorted_apps(
        stats.app_distribution.begin(), stats.app_distribution.end());
    std::sort(sorted_apps.begin(), sorted_apps.end(),
              [](const auto& a, const auto& b) { return a.second > b.second; });

    ss << "[ConnectionTracker] Application breakdown\n";
    for (const auto& pair : sorted_apps) {
        const double pct = total > 0 ? (100.0 * pair.second / total) : 0.0;
        ss << "  - " << appTypeToString(pair.first)
           << ": " << pair.second
           << " (" << std::fixed << std::setprecision(1) << pct << "%)\n";
    }

    if (!stats.top_domains.empty()) {
        ss << "[ConnectionTracker] Top domains\n";
        for (const auto& pair : stats.top_domains) {
            std::string domain = pair.first;
            if (domain.length() > 35) {
                domain = domain.substr(0, 32) + "...";
            }
            ss << "  - " << domain << ": " << pair.second << "\n";
        }
    }

    return ss.str();
}

} // namespace DPI




