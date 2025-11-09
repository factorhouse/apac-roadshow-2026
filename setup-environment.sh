#!/bin/bash

# ==============================================================================
# KartShoppe Training Environment Setup
# ==============================================================================
# This script sets up all prerequisites from a blank slate for the training.
# Run this ONCE before the training session begins.
# ==============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

clear

echo -e "${CYAN}${BOLD}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║     🎓  KartShoppe Training Environment Setup  🎓              ║"
echo "║                                                                ║"
echo "║     This will install and configure all prerequisites         ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${YELLOW}This script will install:${NC}"
echo "  1. SDKMAN (Java version manager)"
echo "  2. Java 11 (for building Flink jobs)"
echo "  3. Java 17 (for running Quarkus)"
echo "  4. Verify Docker Desktop is installed"
echo "  5. Verify Node.js 18+ is installed"
echo ""
echo -e "${YELLOW}Estimated time: 5-10 minutes${NC}"
echo ""
read -p "Press ENTER to begin setup, or Ctrl+C to cancel..."
echo ""

# ==============================================================================
# Step 1: Check Docker Desktop
# ==============================================================================

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 1/5: Checking Docker Desktop${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if command -v docker &> /dev/null; then
    if docker info &> /dev/null; then
        DOCKER_VERSION=$(docker --version)
        echo -e "${GREEN}✓${NC} Docker is installed and running: ${DOCKER_VERSION}"
    else
        echo -e "${RED}✗${NC} Docker is installed but not running"
        echo -e "${YELLOW}  Please start Docker Desktop and run this script again${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗${NC} Docker is not installed"
    echo ""
    echo -e "${YELLOW}Please install Docker Desktop:${NC}"
    if [[ "$OSTYPE" == "darwin"* ]]; then
        echo "  macOS: https://docs.docker.com/desktop/install/mac-install/"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        echo "  Linux: https://docs.docker.com/desktop/install/linux-install/"
    else
        echo "  https://docs.docker.com/get-docker/"
    fi
    exit 1
fi

# ==============================================================================
# Step 2: Check Node.js
# ==============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 2/5: Checking Node.js${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version | sed 's/v//')
    NODE_MAJOR=$(echo $NODE_VERSION | cut -d. -f1)

    if [ "$NODE_MAJOR" -ge 18 ]; then
        echo -e "${GREEN}✓${NC} Node.js ${NODE_VERSION} is installed (requirement: 18+)"
        NPM_VERSION=$(npm --version)
        echo -e "${GREEN}✓${NC} npm ${NPM_VERSION} is installed"
    else
        echo -e "${YELLOW}⚠${NC} Node.js ${NODE_VERSION} is installed, but 18+ is required"
        echo -e "${YELLOW}  Please upgrade Node.js: https://nodejs.org/${NC}"
        exit 1
    fi
else
    echo -e "${RED}✗${NC} Node.js is not installed"
    echo ""
    echo -e "${YELLOW}Please install Node.js 18+:${NC}"
    echo "  https://nodejs.org/ (download LTS version)"
    exit 1
fi

# ==============================================================================
# Step 3: Install SDKMAN
# ==============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 3/5: Installing SDKMAN${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    echo -e "${GREEN}✓${NC} SDKMAN is already installed"
    source "$HOME/.sdkman/bin/sdkman-init.sh"
else
    echo "Installing SDKMAN..."
    curl -s "https://get.sdkman.io" | bash

    # Source SDKMAN
    source "$HOME/.sdkman/bin/sdkman-init.sh"

    echo -e "${GREEN}✓${NC} SDKMAN installed successfully"
fi

# ==============================================================================
# Step 4: Install Java 11
# ==============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 4/5: Installing Java 11 (for Flink jobs)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if Java 11 is already installed
if sdk list java | grep -q "11.0.*-tem.*installed"; then
    echo -e "${GREEN}✓${NC} Java 11 is already installed"
else
    echo "Installing Java 11 (Temurin distribution)..."
    sdk install java 11.0.25-tem < /dev/null
    echo -e "${GREEN}✓${NC} Java 11 installed successfully"
fi

# ==============================================================================
# Step 5: Install Java 17
# ==============================================================================

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}Step 5/5: Installing Java 17 (for Quarkus)${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if Java 17 is already installed
if sdk list java | grep -q "17.0.*-tem.*installed"; then
    echo -e "${GREEN}✓${NC} Java 17 is already installed"
else
    echo "Installing Java 17 (Temurin distribution)..."
    sdk install java 17.0.13-tem < /dev/null
    echo -e "${GREEN}✓${NC} Java 17 installed successfully"
fi

# Set Java 17 as default
echo ""
echo "Setting Java 17 as default..."
sdk default java 17.0.13-tem < /dev/null
echo -e "${GREEN}✓${NC} Java 17 set as default"

# ==============================================================================
# Final Summary
# ==============================================================================

echo ""
echo -e "${GREEN}${BOLD}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                                                                ║"
echo "║              ✨  Setup Completed Successfully!  ✨             ║"
echo "║                                                                ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""
echo -e "${BLUE}Installed Components:${NC}"

# Verify installations
source "$HOME/.sdkman/bin/sdkman-init.sh"

echo -e "  ${GREEN}✓${NC} Docker:     $(docker --version)"
echo -e "  ${GREEN}✓${NC} Node.js:    $(node --version)"
echo -e "  ${GREEN}✓${NC} npm:        $(npm --version)"
echo -e "  ${GREEN}✓${NC} SDKMAN:     $(sdk version)"
echo -e "  ${GREEN}✓${NC} Java 11:    $(sdk list java | grep "11.0.*-tem" | grep installed | awk '{print $8}' | head -1)"
echo -e "  ${GREEN}✓${NC} Java 17:    $(java -version 2>&1 | head -1)"

echo ""
echo -e "${BLUE}Java Version Management:${NC}"
echo -e "  Current default: ${GREEN}Java 17${NC} (for Quarkus)"
echo ""
echo -e "  To switch to Java 11 (for building Flink jobs):"
echo -e "    ${CYAN}sdk use java 11.0.25-tem${NC}"
echo ""
echo -e "  To switch back to Java 17:"
echo -e "    ${CYAN}sdk use java 17.0.13-tem${NC}"

echo ""
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}Next Steps:${NC}"
echo -e "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  1. Start the platform:    ${GREEN}./start-training-platform.sh${NC}"
echo -e "  2. Read training guide:   ${GREEN}cat TRAINING-SETUP.md${NC}"
echo ""
echo -e "${CYAN}You are now ready for the KartShoppe training! 🎉${NC}"
echo ""
