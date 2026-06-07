'use strict'
const path = require('path')
const defaultSettings = require('./src/settings.js')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const name = defaultSettings.title || 'vue Admin Template' // page title

// If your port is set to 80,
// use administrator privileges to execute the command line.
// For example, Mac: sudo npm run
// You can change the port by the following methods:
// port = 9528 npm run dev OR npm run dev --port = 9528
const port = process.env.port || process.env.npm_config_port || 9528 // dev port

// All configuration item explanations can be find in https://cli.vuejs.org/config/
module.exports = {
  /**
   * You will need to set publicPath if you plan to deploy your site under a sub path,
   * for example GitHub Pages. If you plan to deploy your site to https://foo.github.io/bar/,
   * then publicPath should be set to "/bar/".
   * In most cases please use '/' !!!
   * Detail: https://cli.vuejs.org/config/#publicpath
   */
  publicPath: './',
  outputDir: 'dist',
  assetsDir: 'static',
  // lintOnSave: process.env.NODE_ENV === 'development',
  lintOnSave: false,
  productionSourceMap: false,
  devServer: {
    port: port,
    open: true,
    overlay: {
      warnings: false,
      errors: true
    },
    before: require('./mock/mock-server.js')
  },
  transpileDependencies: true,
  productionSourceMap: false, // 生产环境不生成source map
  
  configureWebpack: {
    resolve: {
      alias: {
        '@': resolve('src')
      }
    }
  },
  transpileDependencies: [
    // 将需要 Babel 转换的依赖包名添加到这里
    'gsplat'
  ],
  chainWebpack: config => {
    // 添加一条新的规则，专门用于处理 gsplat 库中的 JS 文件。
    config.module
      .rule('js')
      .include
        // 使用正则表达式来精确地定位到 node_modules/gsplat 目录。
        .add(/node_modules[\\/]gsplat/)
        .end()
      .use('babel-loader')
        .loader('babel-loader')
        .end()
  },
  configureWebpack: {
    // provide the app's title in webpack's name field, so that
    // it can be accessed in index.html to inject the correct title.
    name: name,
    resolve: {
      alias: {
        
        http2: path.resolve(__dirname, "src/utils/empty-module.js"),  // 禁用 http2（浏览器不需要）
        dns: path.resolve(__dirname, "src/utils/empty-module.js"),  
      },
    }
  },
  chainWebpack(config) {
    // 设置svg-sprite-loader
    config.module
      .rule('svg')
      .exclude.add(resolve('src/icons'))
      .end()
    config.module
      .rule('icons')
      .test(/\.svg$/)
      .include.add(resolve('src/icons'))
      .end()
      .use('svg-sprite-loader')
      .loader('svg-sprite-loader')
      .options({
        symbolId: 'icon-[name]'
      })
      .end()
  },
  
  // 确保所有资源都被处理
  assetsDir: 'static',
  
  // 复制文件配置
  chainWebpack: config => {
    config.plugin('copy').tap(args => {
      args[0].push({
        from: path.resolve(__dirname, 'src/assets'),
        to: path.resolve(__dirname, 'dist/assets'),
        ignore: ['.*']
      })
      return args
    })
  },

// devServer:{
//   // 反向代理
//     proxy: {
//       '/api': {
//         target: 'http://localhost:8080', //本地地址
//         changeOrigin: true,
//         pathRewrite: {
//           '^/api': ''
//         },
//       },
//     }
// }

}
