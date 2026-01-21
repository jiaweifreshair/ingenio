import { get, post } from './client';

/**
 * 套餐信息
 */
export interface CreditPackage {
  code: string;
  name: string;
  credits: number;
  price: number;
}

/**
 * 用户余额信息
 */
export interface UserCredits {
  total: number;
  used: number;
  remaining: number;
}

/**
 * 创建订单请求
 */
export interface CreateOrderRequest {
  packageCode: string;
  payChannel: 'ALIPAY_PC' | 'ALIPAY_WAP';
}

/**
 * 创建订单响应
 */
export interface CreateOrderResponse {
  orderNo: string;
  payDataType: string;
  payData: string;
  expireTime: string;
}

/**
 * 支付订单信息
 */
export interface PayOrder {
  orderNo: string;
  packageName: string;
  creditsAmount: number;
  amount: number;
  status: string;
  createdAt: string;
  payTime?: string;
}

/**
 * 获取用户余额
 */
export async function getUserCredits() {
  return get<UserCredits>('/v1/billing/credits');
}

/**
 * 获取套餐列表
 */
export async function getPackages() {
  return get<CreditPackage[]>('/v1/billing/packages');
}

/**
 * 创建支付订单
 */
export async function createOrder(request: CreateOrderRequest) {
  return post<CreateOrderResponse>('/v1/billing/orders', request);
}

/**
 * 查询订单状态
 */
export async function getOrderStatus(orderNo: string) {
  return get<PayOrder>(`/v1/billing/orders/${orderNo}`);
}

/**
 * 获取订单列表
 */
export async function getOrders(page: number = 1, size: number = 10) {
  return get<PayOrder[]>(`/v1/billing/orders?page=${page}&size=${size}`);
}
