#!/bin/bash

# Android Development Memory Management Script
# Optimizes system for memory-intensive operations like 3D mesh generation

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== Android Development Memory Manager ===${NC}"

# Function to display current memory status
show_memory_status() {
    echo -e "\n${BLUE}Current Memory Status:${NC}"
    free -h
    echo -e "\n${BLUE}Swap Usage:${NC}"
    swapon --show
}

# Function to optimize memory settings for Android development
optimize_for_android_dev() {
    echo -e "\n${YELLOW}Optimizing memory settings for Android development...${NC}"
    
    # Set swappiness to 60 for better balance (default is usually 60)
    # Lower values prefer RAM, higher values prefer swap
    echo 60 | sudo tee /proc/sys/vm/swappiness > /dev/null
    
    # Set cache pressure to 50 (default 100)
    # Lower values preserve cache longer
    echo 50 | sudo tee /proc/sys/vm/vfs_cache_pressure > /dev/null
    
    # Drop caches to free up memory
    echo -e "${YELLOW}Clearing system caches...${NC}"
    sync
    echo 3 | sudo tee /proc/sys/vm/drop_caches > /dev/null
    
    echo -e "${GREEN}Memory optimization complete!${NC}"
}

# Function to prepare for memory-intensive operations
prepare_for_intensive_work() {
    echo -e "\n${YELLOW}Preparing for memory-intensive operations...${NC}"
    
    # Force garbage collection for Java processes
    jps | grep -v Jps | while read pid name; do
        echo -e "Requesting GC for Java process: ${name} (${pid})"
        jcmd $pid GC.run_finalization 2>/dev/null || true
        jcmd $pid GC.run 2>/dev/null || true
    done
    
    # Kill unnecessary processes to free memory
    echo -e "Stopping unnecessary services..."
    
    # Stop some non-essential services temporarily
    sudo systemctl stop bluetooth 2>/dev/null || true
    sudo systemctl stop cups 2>/dev/null || true
    
    echo -e "${GREEN}System prepared for intensive operations!${NC}"
}

# Function to restore normal settings
restore_normal_settings() {
    echo -e "\n${YELLOW}Restoring normal memory settings...${NC}"
    
    # Restore default swappiness
    echo 60 | sudo tee /proc/sys/vm/swappiness > /dev/null
    
    # Restore default cache pressure
    echo 100 | sudo tee /proc/sys/vm/vfs_cache_pressure > /dev/null
    
    # Restart services
    sudo systemctl start bluetooth 2>/dev/null || true
    sudo systemctl start cups 2>/dev/null || true
    
    echo -e "${GREEN}Normal settings restored!${NC}"
}

# Function to monitor memory during Android build
monitor_build() {
    echo -e "\n${BLUE}Starting memory monitoring...${NC}"
    echo -e "Press Ctrl+C to stop monitoring\n"
    
    while true; do
        clear
        echo -e "${BLUE}=== Real-time Memory Monitor ===${NC}"
        echo -e "$(date)"
        echo ""
        
        # Memory usage
        free -h
        echo ""
        
        # Top memory consumers
        echo -e "${BLUE}Top Memory Consumers:${NC}"
        ps aux --sort=-%mem | head -10 | awk '{printf "%-8s %-6s %-6s %-10s %s\n", $1, $2, $3, $4, $11}'
        echo ""
        
        # Gradle daemon info if running
        if pgrep -f "GradleDaemon" > /dev/null; then
            echo -e "${YELLOW}Gradle Daemon Status: RUNNING${NC}"
            pgrep -f "GradleDaemon" | while read pid; do
                mem=$(ps -p $pid -o rss= | awk '{print $1/1024 " MB"}')
                echo -e "  PID $pid: ${mem}"
            done
        else
            echo -e "${GREEN}Gradle Daemon Status: NOT RUNNING${NC}"
        fi
        
        sleep 5
    done
}

# Main menu
case "$1" in
    "status")
        show_memory_status
        ;;
    "optimize")
        optimize_for_android_dev
        show_memory_status
        ;;
    "prepare")
        prepare_for_intensive_work
        show_memory_status
        ;;
    "restore")
        restore_normal_settings
        ;;
    "monitor")
        monitor_build
        ;;
    "build")
        echo -e "${BLUE}Optimizing system for Android build...${NC}"
        optimize_for_android_dev
        prepare_for_intensive_work
        echo -e "\n${GREEN}System ready! You can now run your Android build.${NC}"
        echo -e "${YELLOW}Run '$0 restore' after your build to restore normal settings.${NC}"
        ;;
    *)
        echo -e "Usage: $0 {status|optimize|prepare|restore|monitor|build}"
        echo -e ""
        echo -e "Commands:"
        echo -e "  ${GREEN}status${NC}   - Show current memory status"
        echo -e "  ${GREEN}optimize${NC} - Optimize memory settings for development"
        echo -e "  ${GREEN}prepare${NC}  - Prepare for memory-intensive operations"
        echo -e "  ${GREEN}restore${NC}  - Restore normal memory settings"
        echo -e "  ${GREEN}monitor${NC}  - Real-time memory monitoring"
        echo -e "  ${GREEN}build${NC}    - Full optimization for Android builds"
        echo -e ""
        echo -e "Example: $0 build"
        exit 1
        ;;
esac