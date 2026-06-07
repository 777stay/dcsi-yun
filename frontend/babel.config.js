module.exports = {
  
  presets: [
    '@vue/cli-plugin-babel/preset'
  ],
  plugins: [
    // 【增强】这个插件是处理类属性写法的核心，比 class-static-block 更通用
    '@babel/plugin-proposal-class-properties',
    // 处理 'static {}' 语法
    '@babel/plugin-proposal-class-static-block',
    // 处理 'import.meta.url' 语法
    '@babel/plugin-syntax-import-meta'
  ],
  'env': {
    'development': {
      // babel-plugin-dynamic-import-node plugin only does one thing by converting all import() to require().
      // This plugin can significantly increase the speed of hot updates, when you have a large number of pages.
      // https://panjiachen.github.io/vue-element-admin-site/guide/advanced/lazy-loading.html
      'plugins': ['dynamic-import-node']
    }
  }
}
