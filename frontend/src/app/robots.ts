import { MetadataRoute } from 'next';

export default function robots(): MetadataRoute.Robots {
  // 暂时使用占位域名，上线时需替换
  const baseUrl = 'https://ingenio.cn'; 

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/dashboard/', '/api/', '/preview/'],
      },
      {
        // 百度爬虫
        userAgent: 'BaiduSpider',
        allow: '/',
      },
      {
        // 字节跳动爬虫（影响豆包、头条搜索）
        userAgent: 'ByteSpider',
        allow: '/',
      },
      {
        // 搜狗爬虫
        userAgent: 'Sogou web spider',
        allow: '/',
      },
      {
        // 360搜索
        userAgent: '360Spider',
        allow: '/',
      }
    ],
    sitemap: `${baseUrl}/sitemap.xml`,
  };
}
