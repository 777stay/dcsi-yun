import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

/* Layout */
import Layout from '@/layout'

/**
 * Note: sub-menu only appear when route children.length >= 1
 * Detail see: https://panjiachen.github.io/vue-element-admin-site/guide/essentials/router-and-nav.html
 *
 * hidden: true                   if set true, item will not show in the sidebar(default is false)
 * alwaysShow: true               if set true, will always show the root menu
 *                                if not set alwaysShow, when item has more than one children route,
 *                                it will becomes nested mode, otherwise not show the root menu
 * redirect: noRedirect           if set noRedirect will no redirect in the breadcrumb
 * name:'router-name'             the name is used by <keep-alive> (must set!!!)
 * meta : {
    roles: ['admin','editor']    control the page roles (you can set multiple roles)
    title: 'title'               the name show in sidebar and breadcrumb (recommend set)
    icon: 'svg-name'/'el-icon-x' the icon show in the sidebar
    breadcrumb: false            if set false, the item will hidden in breadcrumb(default is true)
    activeMenu: '/example/list'  if set path, the sidebar will highlight the path you set
  }
 */

/**
 * constantRoutes
 * a base page that does not have permission requirements
 * all roles can be accessed
 */
export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login/index'),
    hidden: true
  },

  {
    path: '/register',
    component: () => import('@/views/login/register'),
    hidden: true
  },

  {
    path: '/404',
    component: () => import('@/views/404'),
    hidden: true
  },

  {
    path: '/',
    component: Layout,
    redirect: '/path-planning',
    children: [{
      path: 'path-planning',
      name: 'PathPlanning',
      component: () => import('@/views/form/index'),
      meta: { title: '无人机任务规划', icon: 'el-icon-map-location' }
    }]
  },

  {
    path: '/real-time',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'RealTime',
        component: () => import('@/views/nested/index'),
        meta: { title: '卫星地图轨迹查看', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/pointCloud-map',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'PCM',
        component: () => import('@/views/pointCloudMap/index'),
        meta: { title: '机器人设备管理', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/multiView',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'multiView',
        component: () => import('@/views/multiView/index'),
        meta: { title: '多视角查看', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/potree',
    component: Layout,
    children: [
      {
        path: 'indexcopy',
        name: 'pt',
        component: () => import('@/views/potree/indexcopy'),
        meta: { title: '点云数据查看', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/fusion',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'FusionPotree',
        component: () => import('@/views/fusion/index'),
        meta: { title: '多机点云融合', icon: 'el-icon-s-data' }
      }
    ]
  },
  {
    path: '/robotManager',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'robotManager',
        component: () => import('@/views/robotManager/index'),
        meta: { title: '机器人数据管理', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/kmlManager',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'kmlManager',
        component: () => import('@/views/robotManager/kmlIndex'),
        meta: { title: 'kml管理', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/testImage',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'testImage',
        component: () => import('@/views/testImage/index'),
        meta: { title: '图像检测', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/plyDetection',
    component:Layout,
    children: [
      {
        path: 'index',
        name: 'plyDetection',
        component: () => import('@/views/plyDetection/index'),
        meta: { title: '点云检测', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: '/robot/:robotId', // `:robotId` 是一个动态参数
    name: 'robot-detail',
    component: () => import('@/views/pointCloudMap/RobotDetailView'),
   
  },
  {
    path: '/splat3DGS',
    component: Layout,
    children: [
      {
        path: 'index',
        name: 'splat3DGS',
        component: () => import('@/views/splat3DGS/index'),
        meta: { title: 'splat3DGS', icon: 'el-icon-location-information' }
      }
    ]
  },
  {
    path: 'external-link',
    component: Layout,
    children: [
      {
        path: 'https://panjiachen.github.io/vue-element-admin-site/#/',
        meta: { title: 'External Link', icon: 'link' }
      }
    ]
  },

  // 404 page must be placed at the end !!!
  { path: '*', redirect: '/404', hidden: true }
]

const createRouter = () => new Router({
  mode: 'hash', // require service support
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

// Detail see: https://github.com/vuejs/vue-router/issues/1234#issuecomment-357941465
export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher // reset router
}

export default router
