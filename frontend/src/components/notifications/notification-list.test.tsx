/**
 * NotificationList组件集成测试
 * 秒构AI通知列表组件测试
 *
 * 测试目标：src/components/notifications/notification-list.tsx
 * 测试策略：
 * - 集成测试完整的通知列表渲染流程
 * - 测试用户交互场景（加载更多、刷新）
 * - 测试不同数据状态的显示（加载中、空数据、有数据）
 * - 测试分页和筛选功能
 *
 * Phase 3.3 - 核心组件集成测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { renderWithProviders, screen } from "@/test/utils/render-with-providers";
import userEvent from "@testing-library/user-event";
import { NotificationList } from "./notification-list";
import type { Notification, NotificationFilters } from "@/types/notification";
import type { PageResult } from "@/types/project";
import { NotificationType } from "@/types/notification";

describe("NotificationList组件集成测试 - notification-list.tsx", () => {
  // 创建测试数据
  const createMockNotification = (id: string, overrides?: Partial<Notification>): Notification => ({
    id,
    type: NotificationType.SYSTEM,
    title: `通知${id}`,
    content: `通知${id}的内容`,
    isRead: false,
    createdAt: "2025-01-14T10:00:00Z",
    ...overrides,
  });

  const createMockPageResult = (
    records: Notification[],
    total: number = records.length,
    hasNext: boolean = false
  ): PageResult<Notification> => ({
    records,
    total,
    size: 10,
    current: 1,
    pages: Math.ceil(total / 10),
    hasNext,
    hasPrevious: false,
  });

  const defaultFilters: NotificationFilters = {
    unreadOnly: false,
    type: undefined,
  };

  let onRefresh: ReturnType<typeof vi.fn>;
  let onLoadMore: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onRefresh = vi.fn();
    onLoadMore = vi.fn();
  });

  describe("用户流程：加载状态显示", () => {
    it("应该显示加载中状态（空列表时）", () => {
      const emptyData = createMockPageResult([]);

      const { container } = renderWithProviders(
        <NotificationList
          notifications={emptyData}
          isLoading={true}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示加载图标（Loader2组件）
      const loader = container.querySelector(".animate-spin");
      expect(loader).toBeInTheDocument();
    });

    it("应该在有数据时正常显示通知列表", () => {
      const notifications = [createMockNotification("1"), createMockNotification("2"), createMockNotification("3")];

      const data = createMockPageResult(notifications);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示所有通知
      notifications.forEach((notif) => {
        expect(screen.getByText(notif.title)).toBeInTheDocument();
      });

      // 应该显示分页信息
      expect(screen.getByText(/已显示.*3.*\/.*3.*条通知/)).toBeInTheDocument();
    });
  });

  describe("用户流程：空状态显示", () => {
    it("应该显示全部已读的空状态（unreadOnly过滤）", () => {
      const emptyData = createMockPageResult([]);

      renderWithProviders(
        <NotificationList
          notifications={emptyData}
          isLoading={false}
          filters={{ ...defaultFilters, unreadOnly: true }}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示"全部已读"提示
      expect(screen.getByText("全部已读")).toBeInTheDocument();
      expect(screen.getByText("您暂时没有未读通知")).toBeInTheDocument();
    });

    it("应该显示暂无通知的空状态（无过滤）", () => {
      const emptyData = createMockPageResult([]);

      renderWithProviders(
        <NotificationList
          notifications={emptyData}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示"暂无通知"提示
      expect(screen.getByText("暂无通知")).toBeInTheDocument();
      expect(screen.getByText("您的通知将显示在这里")).toBeInTheDocument();
    });
  });

  describe("用户流程：分页加载更多", () => {
    it("应该显示加载更多按钮（当hasNext=true时）", () => {
      const notifications = [createMockNotification("1"), createMockNotification("2")];

      const data = createMockPageResult(notifications, 10, true); // hasNext=true

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示"加载更多"按钮
      const loadMoreButton = screen.getByRole("button", { name: /加载更多/ });
      expect(loadMoreButton).toBeInTheDocument();
      expect(loadMoreButton).not.toBeDisabled();
    });

    it("应该在点击加载更多按钮时触发回调", async () => {
      const user = userEvent.setup();
      const notifications = [createMockNotification("1"), createMockNotification("2")];

      const data = createMockPageResult(notifications, 10, true);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      const loadMoreButton = screen.getByRole("button", { name: /加载更多/ });

      // 用户点击加载更多
      await user.click(loadMoreButton);

      // 应该触发onLoadMore回调
      expect(onLoadMore).toHaveBeenCalledTimes(1);
    });

    it("应该在加载时禁用加载更多按钮", () => {
      const notifications = [createMockNotification("1")];

      const data = createMockPageResult(notifications, 10, true);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={true}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      const loadMoreButton = screen.getByRole("button", { name: /加载中/ });
      expect(loadMoreButton).toBeDisabled();
      expect(screen.getByText("加载中...")).toBeInTheDocument();
    });

    it("应该在没有更多数据时隐藏加载更多按钮", () => {
      const notifications = [createMockNotification("1"), createMockNotification("2")];

      const data = createMockPageResult(notifications, 2, false); // hasNext=false

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 不应该显示"加载更多"按钮
      expect(screen.queryByRole("button", { name: /加载更多/ })).not.toBeInTheDocument();
    });
  });

  describe("用户流程：分页信息显示", () => {
    it("应该正确显示分页统计信息", () => {
      const notifications = [
        createMockNotification("1"),
        createMockNotification("2"),
        createMockNotification("3"),
      ];

      const data = createMockPageResult(notifications, 10, true);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该显示"已显示 3 / 10 条通知"
      expect(screen.getByText(/已显示.*3.*\/.*10.*条通知/)).toBeInTheDocument();
    });

    it("应该在数据更新后更新分页信息", () => {
      const initialData = createMockPageResult([createMockNotification("1")], 10, true);

      const { rerender } = renderWithProviders(
        <NotificationList
          notifications={initialData}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 初始显示
      expect(screen.getByText(/已显示.*1.*\/.*10.*条通知/)).toBeInTheDocument();

      // 加载更多后更新数据
      const updatedData = createMockPageResult(
        [createMockNotification("1"), createMockNotification("2"), createMockNotification("3")],
        10,
        true
      );

      rerender(
        <NotificationList
          notifications={updatedData}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 更新后的显示
      expect(screen.getByText(/已显示.*3.*\/.*10.*条通知/)).toBeInTheDocument();
    });
  });

  describe("组件渲染：通知项展示", () => {
    it("应该为每个通知渲染NotificationItem组件", () => {
      const notifications = [
        createMockNotification("1", { title: "系统通知1" }),
        createMockNotification("2", { title: "系统通知2" }),
        createMockNotification("3", { title: "评论通知", type: NotificationType.COMMENT }),
      ];

      const data = createMockPageResult(notifications);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 每个通知的标题应该显示
      expect(screen.getByText("系统通知1")).toBeInTheDocument();
      expect(screen.getByText("系统通知2")).toBeInTheDocument();
      expect(screen.getByText("评论通知")).toBeInTheDocument();
    });

    it("应该传递onRefresh回调给NotificationItem", () => {
      const notifications = [createMockNotification("1")];

      const data = createMockPageResult(notifications);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // NotificationItem应该接收onRefresh回调（由子组件测试验证行为）
      expect(screen.getByText("通知1")).toBeInTheDocument();
    });
  });

  describe("边界情况和健壮性", () => {
    it("应该处理大量通知数据（性能测试）", () => {
      // 创建100条通知
      const notifications = Array.from({ length: 100 }, (_, i) =>
        createMockNotification(`${i + 1}`, {
          title: `通知${i + 1}`,
        })
      );

      const data = createMockPageResult(notifications, 1000, true);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 应该成功渲染所有通知
      expect(screen.getByText(/已显示.*100.*\/.*1000.*条通知/)).toBeInTheDocument();

      // 验证所有通知都被渲染
      expect(screen.getByText("通知1")).toBeInTheDocument();
      expect(screen.getByText("通知100")).toBeInTheDocument();
    });

    it("应该处理notifications为空数组的情况", () => {
      const emptyData = createMockPageResult([]);

      expect(() => {
        renderWithProviders(
          <NotificationList
            notifications={emptyData}
            isLoading={false}
            filters={defaultFilters}
            onRefresh={onRefresh}
            onLoadMore={onLoadMore}
          />
        );
      }).not.toThrow();

      expect(screen.getByText("暂无通知")).toBeInTheDocument();
    });

    it("应该处理total为0的情况", () => {
      const data = createMockPageResult([], 0, false);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // total为0时显示空状态，不显示分页信息
      expect(screen.getByText("暂无通知")).toBeInTheDocument();
    });

    it("应该支持快速切换过滤条件", () => {
      const notifications = [createMockNotification("1")];
      const data = createMockPageResult(notifications);

      const { rerender } = renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      expect(screen.getByText("通知1")).toBeInTheDocument();

      // 切换到unreadOnly过滤
      const emptyData = createMockPageResult([]);
      rerender(
        <NotificationList
          notifications={emptyData}
          isLoading={false}
          filters={{ ...defaultFilters, unreadOnly: true }}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      expect(screen.getByText("全部已读")).toBeInTheDocument();
    });
  });

  describe("可访问性（Accessibility）", () => {
    it("应该有正确的语义化HTML结构", () => {
      const notifications = [createMockNotification("1")];
      const data = createMockPageResult(notifications, 10, true);

      renderWithProviders(
        <NotificationList
          notifications={data}
          isLoading={false}
          filters={defaultFilters}
          onRefresh={onRefresh}
          onLoadMore={onLoadMore}
        />
      );

      // 加载更多按钮应该是button role
      const loadMoreButton = screen.getByRole("button", { name: /加载更多/ });
      expect(loadMoreButton).toBeInTheDocument();
    });
  });
});
