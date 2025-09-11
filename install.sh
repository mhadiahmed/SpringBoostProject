#!/bin/bash

# Spring Boost Installation Script
# This script provides multiple installation methods for Spring Boost

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
INSTALL_METHOD=""
VERSION="latest"
INSTALL_DIR="${HOME}/.spring-boost"
JAVA_VERSION="17"

# Functions
print_banner() {
    echo -e "${BLUE}"
    echo "  ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗     ██████╗  ██████╗  ██████╗ ███████╗████████╗"
    echo "  ██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝     ██╔══██╗██╔═══██╗██╔═══██╗██╔════╝╚══██╔══╝"
    echo "  ███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗    ██████╔╝██║   ██║██║   ██║███████╗   ██║   "
    echo "  ╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║    ██╔══██╗██║   ██║██║   ██║╚════██║   ██║   "
    echo "  ███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝    ██████╔╝╚██████╔╝╚██████╔╝███████║   ██║   "
    echo "  ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝     ╚═════╝  ╚═════╝  ╚═════╝ ╚══════╝   ╚═╝   "
    echo -e "${NC}"
    echo -e "${BLUE}Spring Boost - MCP Server for AI-Assisted Spring Boot Development${NC}"
    echo -e "${BLUE}The Laravel Boost equivalent for Spring Boot developers${NC}"
    echo ""
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check Java
    if command -v java >/dev/null 2>&1; then
        JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F'"' '{print $2}' | cut -d'.' -f1)
        if [ "$JAVA_VER" -ge "$JAVA_VERSION" ]; then
            log_success "Java ${JAVA_VER} found"
        else
            log_error "Java ${JAVA_VERSION} or higher is required. Found: ${JAVA_VER}"
            exit 1
        fi
    else
        log_error "Java not found. Please install Java ${JAVA_VERSION} or higher"
        exit 1
    fi
    
    # Check if curl or wget is available
    if ! command -v curl >/dev/null 2>&1 && ! command -v wget >/dev/null 2>&1; then
        log_error "curl or wget is required for download"
        exit 1
    fi
}

download_file() {
    local url=$1
    local output=$2
    
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$output" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$output" "$url"
    else
        log_error "No download tool available"
        return 1
    fi
}

install_jar() {
    log_info "Installing Spring Boost JAR..."
    
    # Create installation directory
    mkdir -p "$INSTALL_DIR"
    
    # Download latest release
    if [ "$VERSION" = "latest" ]; then
        # For now, we'll build from source since this is initial release
        log_info "Building from source..."
        
        if [ ! -d "spring-boost" ]; then
            git clone https://github.com/springboost/spring-boost.git
        fi
        
        cd spring-boost
        
        if command -v mvn >/dev/null 2>&1; then
            ./mvnw clean package -DskipTests
            cp target/spring-boost-*.jar "$INSTALL_DIR/spring-boost.jar"
        elif command -v gradle >/dev/null 2>&1; then
            ./gradlew bootJar
            cp build/libs/spring-boost-*.jar "$INSTALL_DIR/spring-boost.jar"
        else
            log_error "Maven or Gradle is required to build from source"
            exit 1
        fi
        
        cd ..
    else
        # Download specific version
        download_file "https://github.com/springboost/spring-boost/releases/download/v${VERSION}/spring-boost-${VERSION}.jar" \
                     "$INSTALL_DIR/spring-boost.jar"
    fi
    
    # Create wrapper script
    cat > "$INSTALL_DIR/spring-boost" << 'EOF'
#!/bin/bash
SPRING_BOOST_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec java -jar "$SPRING_BOOST_HOME/spring-boost.jar" "$@"
EOF
    
    chmod +x "$INSTALL_DIR/spring-boost"
    
    # Add to PATH
    if [ -f ~/.bashrc ]; then
        echo "export PATH=\"$INSTALL_DIR:\$PATH\"" >> ~/.bashrc
        log_info "Added Spring Boost to ~/.bashrc"
    fi
    
    if [ -f ~/.zshrc ]; then
        echo "export PATH=\"$INSTALL_DIR:\$PATH\"" >> ~/.zshrc
        log_info "Added Spring Boost to ~/.zshrc"
    fi
    
    log_success "Spring Boost installed to $INSTALL_DIR"
    log_info "Please restart your shell or run: source ~/.bashrc (or ~/.zshrc)"
}

install_docker() {
    log_info "Installing Spring Boost with Docker..."
    
    # Check Docker
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is required for Docker installation"
        exit 1
    fi
    
    # Create docker-compose.yml
    mkdir -p "$INSTALL_DIR"
    cat > "$INSTALL_DIR/docker-compose.yml" << 'EOF'
version: '3.8'
services:
  spring-boost:
    image: springboost/spring-boost:latest
    ports:
      - "8080:8080"
      - "28080:28080"
    environment:
      - SPRING_PROFILES_ACTIVE=production
    volumes:
      - spring-boost-data:/app/data
    restart: unless-stopped

volumes:
  spring-boost-data:
EOF
    
    # Pull and start
    cd "$INSTALL_DIR"
    docker-compose pull
    docker-compose up -d
    
    log_success "Spring Boost started with Docker"
    log_info "MCP server available at: ws://localhost:28080"
    log_info "Management interface: http://localhost:8080"
}

install_maven() {
    log_info "Adding Spring Boost Maven dependency..."
    
    echo "Add the following dependency to your pom.xml:"
    echo ""
    echo -e "${YELLOW}<dependency>"
    echo "    <groupId>com.springboost</groupId>"
    echo "    <artifactId>spring-boost</artifactId>"
    echo "    <version>1.0.0</version>"
    echo "</dependency>${NC}"
    echo ""
    echo "Then run: mvn spring-boot:run"
}

install_gradle() {
    log_info "Adding Spring Boost Gradle dependency..."
    
    echo "Add the following dependency to your build.gradle:"
    echo ""
    echo -e "${YELLOW}implementation 'com.springboost:spring-boost:1.0.0'${NC}"
    echo ""
    echo "Then run: ./gradlew bootRun"
}

show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -m, --method METHOD    Installation method: jar, docker, maven, gradle"
    echo "  -v, --version VERSION  Version to install (default: latest)"
    echo "  -d, --dir DIRECTORY    Installation directory (default: ~/.spring-boost)"
    echo "  -h, --help            Show this help message"
    echo ""
    echo "Installation methods:"
    echo "  jar      Download and install standalone JAR"
    echo "  docker   Install and run with Docker"
    echo "  maven    Show Maven dependency instructions"
    echo "  gradle   Show Gradle dependency instructions"
    echo ""
    echo "Examples:"
    echo "  $0 --method jar"
    echo "  $0 --method docker"
    echo "  $0 --method maven"
    echo "  curl -sSL https://install.springboost.com | bash -s -- --method jar"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -m|--method)
            INSTALL_METHOD="$2"
            shift 2
            ;;
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -d|--dir)
            INSTALL_DIR="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main installation flow
main() {
    print_banner
    
    if [ -z "$INSTALL_METHOD" ]; then
        echo "Select installation method:"
        echo "1) Standalone JAR"
        echo "2) Docker"
        echo "3) Maven dependency"
        echo "4) Gradle dependency"
        echo ""
        read -p "Enter choice [1-4]: " choice
        
        case $choice in
            1) INSTALL_METHOD="jar" ;;
            2) INSTALL_METHOD="docker" ;;
            3) INSTALL_METHOD="maven" ;;
            4) INSTALL_METHOD="gradle" ;;
            *) log_error "Invalid choice"; exit 1 ;;
        esac
    fi
    
    case $INSTALL_METHOD in
        jar)
            check_prerequisites
            install_jar
            ;;
        docker)
            install_docker
            ;;
        maven)
            install_maven
            ;;
        gradle)
            install_gradle
            ;;
        *)
            log_error "Invalid installation method: $INSTALL_METHOD"
            show_usage
            exit 1
            ;;
    esac
    
    echo ""
    log_success "Spring Boost installation completed!"
    echo ""
    echo "Next steps:"
    echo "1. Configure your AI client to connect to: ws://localhost:28080"
    echo "2. Start using Spring Boost tools in your AI assistant"
    echo "3. Visit https://docs.springboost.com for documentation"
    echo ""
    echo "Need help? Check out:"
    echo "  GitHub: https://github.com/springboost/spring-boost"
    echo "  Docs:   https://docs.springboost.com"
    echo "  Issues: https://github.com/springboost/spring-boost/issues"
}

# Run main function
main
