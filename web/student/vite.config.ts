import autoprefixer from 'autoprefixer'; // css自动添加兼容性前缀
import { defineConfig, loadEnv } from 'vite';
import createPlugins from './vite/plugins';

export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd());
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: env.VITE_APP_ASSET_BASE || env.VITE_APP_CONTEXT_PATH,
    resolve: {
      tsconfigPaths: true,
      // Each student API selects its in-memory implementation only when
      // VITE_APP_STUDENT_MOCK is explicitly true; do not redirect modules to
      // separate preview-only files.
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // https://cn.vitejs.dev/config/#resolve-extensions
    plugins: createPlugins(env, command === 'build'),
    build: {
      chunkSizeWarningLimit: 1500,
      rolldownOptions: {
        checks: {
          invalidAnnotation: false,
          pluginTimings: false
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: Number(env.VITE_APP_PORT),
      open: true,
      proxy: {
        [env.VITE_APP_BASE_API]: {
          target: env.VITE_DEV_PROXY_TARGET || 'http://localhost:18080',
          changeOrigin: true,
          ws: true,
          rewrite: path => path.replace(new RegExp('^' + env.VITE_APP_BASE_API), '')
        },
        '/oss-proxy/': {
          target: env.VITE_OSS_PROXY_TARGET || 'http://localhost:9000',
          changeOrigin: false,
          rewrite: path => path.replace(/^\/oss-proxy\/(?:host\.docker\.internal|minio|127\.0\.0\.1):9000\//, '/'),
          configure: proxy => {
            proxy.on('proxyReq', (proxyRequest, request) => {
              const signedHost = request.url?.match(
                /^\/oss-proxy\/((?:host\.docker\.internal|minio|127\.0\.0\.1):9000)\//
              )?.[1];
              if (signedHost) proxyRequest.setHeader('Host', signedHost);
            });
          }
        }
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          // additionalData: '@use "@/assets/styles/variables.module.scss as *";'
          // javascriptEnabled: true
        }
      },
      postcss: {
        plugins: [
          // 浏览器兼容性
          autoprefixer(),
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: atRule => {
                atRule.remove();
              }
            }
          }
        ]
      }
    }
  };
});
