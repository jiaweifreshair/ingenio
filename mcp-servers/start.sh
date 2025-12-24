#!/bin/bash

# MCP服务器启动脚本
# 用途: 快速启动和管理MCP服务器

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
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

# 检查Docker是否运行
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        log_error "Docker未运行，请先启动Docker"
        exit 1
    fi
    log_success "Docker运行正常"
}

# 检查Docker Compose是否安装
check_docker_compose() {
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装"
        exit 1
    fi
    log_success "Docker Compose已安装"
}

# 构建镜像
build() {
    log_info "开始构建Docker镜像..."
    docker-compose build
    log_success "镜像构建完成"
}

# 启动服务
start() {
    log_info "启动MCP服务器..."
    docker-compose up -d
    log_success "MCP服务器已启动"

    # 等待服务启动
    sleep 3

    # 显示服务状态
    status
}

# 停止服务
stop() {
    log_info "停止MCP服务器..."
    docker-compose down
    log_success "MCP服务器已停止"
}

# 重启服务
restart() {
    log_info "重启MCP服务器..."
    stop
    start
}

# 查看服务状态
status() {
    log_info "MCP服务器状态:"
    docker-compose ps
}

# 查看日志
logs() {
    log_info "显示MCP服务器日志..."
    if [ -n "$1" ]; then
        docker-compose logs -f --tail=100 "$1"
    else
        docker-compose logs -f --tail=100
    fi
}

# 进入容器
exec_shell() {
    local service="${1:-chrome-devtools-mcp}"
    log_info "进入 $service 容器..."
    docker-compose exec "$service" /bin/bash
}

# 清理数据
clean() {
    log_warning "这将删除所有Docker卷和数据！"
    read -p "确定继续吗？(y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "停止并删除服务..."
        docker-compose down -v
        log_success "清理完成"
    else
        log_info "取消清理操作"
    fi
}

# 测试Chrome DevTools MCP
test_chrome() {
    log_info "测试Chrome DevTools MCP..."

    # 检查容器是否运行
    if ! docker-compose ps | grep -q "chrome-devtools-mcp.*Up"; then
        log_error "Chrome DevTools MCP容器未运行"
        return 1
    fi

    # 测试Chrome是否可用
    if docker-compose exec -T chrome-devtools-mcp which google-chrome-stable > /dev/null 2>&1; then
        log_success "Chrome已安装"
    else
        log_error "Chrome未找到"
        return 1
    fi

    # 显示Chrome版本
    local chrome_version=$(docker-compose exec -T chrome-devtools-mcp google-chrome-stable --version)
    log_info "Chrome版本: $chrome_version"

    log_success "Chrome DevTools MCP测试通过"
}

# 显示帮助信息
show_help() {
    cat << EOF
${GREEN}MCP服务器管理脚本${NC}

用法: $0 [命令]

命令:
    build       构建Docker镜像
    start       启动MCP服务器
    stop        停止MCP服务器
    restart     重启MCP服务器
    status      查看服务状态
    logs [服务]  查看日志（可选指定服务名称）
    shell [服务] 进入容器shell（默认chrome-devtools-mcp）
    test        测试Chrome DevTools MCP
    clean       清理所有数据（谨慎使用！）
    help        显示此帮助信息

示例:
    $0 start                    # 启动所有服务
    $0 logs chrome-devtools-mcp # 查看特定服务日志
    $0 shell                    # 进入默认容器
    $0 test                     # 测试Chrome DevTools MCP

EOF
}

# 主函数
main() {
    # 检查依赖
    check_docker
    check_docker_compose

    # 进入脚本所在目录
    cd "$(dirname "$0")"

    # 处理命令
    case "${1:-help}" in
        build)
            build
            ;;
        start)
            start
            ;;
        stop)
            stop
            ;;
        restart)
            restart
            ;;
        status)
            status
            ;;
        logs)
            logs "$2"
            ;;
        shell)
            exec_shell "$2"
            ;;
        test)
            test_chrome
            ;;
        clean)
            clean
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $1"
            show_help
            exit 1
            ;;
    esac
}

# 运行主函数
main "$@"
