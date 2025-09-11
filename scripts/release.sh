#!/bin/bash

# Spring Boost Release Script
# Automates version bumping and release preparation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CURRENT_VERSION=""
NEW_VERSION=""
RELEASE_TYPE=""
DRY_RUN=false
SKIP_TESTS=false

# Functions
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

show_usage() {
    echo "Usage: $0 [OPTIONS] RELEASE_TYPE"
    echo ""
    echo "Release types:"
    echo "  major     Increment major version (1.0.0 -> 2.0.0)"
    echo "  minor     Increment minor version (1.0.0 -> 1.1.0)"
    echo "  patch     Increment patch version (1.0.0 -> 1.0.1)"
    echo "  rc        Create release candidate (1.0.0 -> 1.0.0-RC.1)"
    echo "  beta      Create beta release (1.0.0 -> 1.0.0-beta)"
    echo "  X.Y.Z     Set specific version"
    echo ""
    echo "Options:"
    echo "  -d, --dry-run     Show what would be done without making changes"
    echo "  -s, --skip-tests  Skip running tests"
    echo "  -h, --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 minor                    # 1.0.0 -> 1.1.0"
    echo "  $0 1.2.3                   # Set to 1.2.3"
    echo "  $0 --dry-run patch         # Preview patch release"
}

get_current_version() {
    CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
    log_info "Current version: $CURRENT_VERSION"
}

calculate_new_version() {
    case $RELEASE_TYPE in
        major)
            NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{printf "%d.0.0", $1+1}')
            ;;
        minor)
            NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{printf "%d.%d.0", $1, $2+1}')
            ;;
        patch)
            NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{printf "%d.%d.%d", $1, $2, $3+1}')
            ;;
        rc)
            if [[ $CURRENT_VERSION =~ -RC\. ]]; then
                # Increment RC number
                NEW_VERSION=$(echo $CURRENT_VERSION | awk -F'-RC.' '{printf "%s-RC.%d", $1, $2+1}')
            else
                # First RC
                NEW_VERSION="${CURRENT_VERSION}-RC.1"
            fi
            ;;
        beta)
            NEW_VERSION="${CURRENT_VERSION}-beta"
            ;;
        [0-9]*)
            NEW_VERSION=$RELEASE_TYPE
            ;;
        *)
            log_error "Invalid release type: $RELEASE_TYPE"
            show_usage
            exit 1
            ;;
    esac
    
    log_info "New version: $NEW_VERSION"
}

validate_version() {
    if [[ ! $NEW_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9\.]+)?$ ]]; then
        log_error "Invalid version format: $NEW_VERSION"
        exit 1
    fi
    
    # Check if tag already exists
    if git tag | grep -q "^v$NEW_VERSION$"; then
        log_error "Tag v$NEW_VERSION already exists"
        exit 1
    fi
}

check_git_status() {
    if [[ -n $(git status --porcelain) ]]; then
        log_error "Working directory is not clean. Please commit or stash changes."
        exit 1
    fi
    
    # Check if on main branch
    CURRENT_BRANCH=$(git branch --show-current)
    if [[ $CURRENT_BRANCH != "main" ]]; then
        log_warning "You are on branch '$CURRENT_BRANCH', not 'main'"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

run_tests() {
    if [[ $SKIP_TESTS == true ]]; then
        log_warning "Skipping tests as requested"
        return
    fi
    
    log_info "Running tests..."
    if [[ $DRY_RUN == false ]]; then
        ./mvnw clean test
        log_success "All tests passed"
    else
        log_info "Would run: ./mvnw clean test"
    fi
}

update_version() {
    log_info "Updating version to $NEW_VERSION..."
    
    if [[ $DRY_RUN == false ]]; then
        # Update Maven version
        ./mvnw versions:set -DnewVersion=$NEW_VERSION -DgenerateBackupPoms=false
        
        # Update Gradle version
        if [[ -f gradle.properties ]]; then
            sed -i "s/^version=.*/version=$NEW_VERSION/" gradle.properties
        fi
        
        # Update Docker Compose version
        if [[ -f docker-compose.yml ]]; then
            sed -i "s/springboost\/spring-boost:[^[:space:]]*/springboost\/spring-boost:$NEW_VERSION/" docker-compose.yml
        fi
        
        # Update README badges
        if [[ -f README.md ]]; then
            sed -i "s/spring-boost-[0-9]\+\.[0-9]\+\.[0-9]\+/spring-boost-$NEW_VERSION/g" README.md
        fi
        
        log_success "Version updated to $NEW_VERSION"
    else
        log_info "Would update version to $NEW_VERSION in:"
        log_info "  - pom.xml"
        log_info "  - gradle.properties"
        log_info "  - docker-compose.yml"
        log_info "  - README.md"
    fi
}

update_changelog() {
    log_info "Updating CHANGELOG.md..."
    
    if [[ $DRY_RUN == false ]]; then
        # Get current date
        CURRENT_DATE=$(date +%Y-%m-%d)
        
        # Update changelog
        sed -i "s/## \[Unreleased\]/## [Unreleased]\n\n### Added\n- New features will be listed here\n\n### Changed\n- Changes will be listed here\n\n### Fixed\n- Bug fixes will be listed here\n\n## [$NEW_VERSION] - $CURRENT_DATE/" CHANGELOG.md
        
        log_success "CHANGELOG.md updated"
    else
        log_info "Would update CHANGELOG.md with release $NEW_VERSION"
    fi
}

commit_changes() {
    log_info "Committing changes..."
    
    if [[ $DRY_RUN == false ]]; then
        git add .
        git commit -m "chore: bump version to $NEW_VERSION"
        log_success "Changes committed"
    else
        log_info "Would commit changes with message: 'chore: bump version to $NEW_VERSION'"
    fi
}

create_tag() {
    log_info "Creating git tag v$NEW_VERSION..."
    
    if [[ $DRY_RUN == false ]]; then
        git tag -a "v$NEW_VERSION" -m "Release $NEW_VERSION"
        log_success "Tag v$NEW_VERSION created"
    else
        log_info "Would create tag: v$NEW_VERSION"
    fi
}

push_changes() {
    log_info "Pushing changes and tags..."
    
    if [[ $DRY_RUN == false ]]; then
        git push origin main
        git push origin "v$NEW_VERSION"
        log_success "Changes and tags pushed"
    else
        log_info "Would push:"
        log_info "  - main branch"
        log_info "  - tag v$NEW_VERSION"
    fi
}

show_next_steps() {
    echo ""
    log_success "Release preparation completed!"
    echo ""
    echo "Next steps:"
    echo "1. Monitor the GitHub Actions release workflow"
    echo "2. Verify deployment to Maven Central"
    echo "3. Check Docker Hub for new image"
    echo "4. Update documentation if needed"
    echo "5. Announce the release"
    echo ""
    echo "Release workflow: https://github.com/springboost/spring-boost/actions"
    echo "Maven Central: https://central.sonatype.com/artifact/com.springboost/spring-boost"
    echo "Docker Hub: https://hub.docker.com/r/springboost/spring-boost"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        -s|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        -*)
            log_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
        *)
            RELEASE_TYPE="$1"
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z $RELEASE_TYPE ]]; then
    log_error "Release type is required"
    show_usage
    exit 1
fi

# Main execution
main() {
    log_info "Starting Spring Boost release process..."
    echo ""
    
    if [[ $DRY_RUN == true ]]; then
        log_warning "DRY RUN MODE - No changes will be made"
        echo ""
    fi
    
    get_current_version
    calculate_new_version
    validate_version
    
    echo ""
    log_info "Release summary:"
    log_info "  Current version: $CURRENT_VERSION"
    log_info "  New version:     $NEW_VERSION"
    log_info "  Release type:    $RELEASE_TYPE"
    log_info "  Dry run:         $DRY_RUN"
    log_info "  Skip tests:      $SKIP_TESTS"
    echo ""
    
    if [[ $DRY_RUN == false ]]; then
        read -p "Continue with release? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Release cancelled"
            exit 0
        fi
    fi
    
    check_git_status
    run_tests
    update_version
    update_changelog
    commit_changes
    create_tag
    
    if [[ $DRY_RUN == false ]]; then
        read -p "Push changes and trigger release? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            push_changes
            show_next_steps
        else
            log_info "Changes prepared but not pushed. Run 'git push origin main && git push origin v$NEW_VERSION' to trigger release."
        fi
    else
        show_next_steps
    fi
}

# Run main function
main
