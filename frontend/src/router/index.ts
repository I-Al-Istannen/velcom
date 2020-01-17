import Vue from 'vue'
import VueRouter, { RouteConfig, RouterOptions } from 'vue-router'
import Home from '../views/Home.vue'
import RepoComparison from '../views/RepoComparison.vue'
import RepoDetailFrame from '../views/RepoDetailFrame.vue'
import RepoDetail from '../views/RepoDetail.vue'
import Queue from '../views/Queue.vue'
import CommitComparison from '../views/CommitComparison.vue'
import CommitDetail from '../views/CommitDetail.vue'
import {
  mdiHome,
  mdiScaleBalance,
  mdiSourceBranch,
  mdiInformationOutline,
  mdiCircleSlice6
} from '@mdi/js'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    redirect: '/home',
    meta: {
      navigable: false
    }
  },
  {
    // Redirect / to /home
    path: '/home',
    name: 'home',
    component: Home,
    meta: {
      label: 'Home',
      navigable: true,
      icon: mdiHome
    }
  },
  {
    path: '/repo-comparison',
    name: 'repo-comparison',
    component: RepoComparison,
    meta: {
      label: 'Repo Comparison',
      navigable: true,
      icon: mdiScaleBalance
    }
  },
  {
    path: '/repo-detail',
    name: 'repo-detail-frame',
    component: RepoDetailFrame,
    children: [
      {
        path: ':id',
        name: 'repo-detail',
        component: RepoDetail
      }
    ],
    meta: {
      label: 'Repo Detail',
      navigable: true,
      icon: mdiSourceBranch
    }
  },
  {
    path: '/queue',
    name: 'queue',
    component: Queue,
    meta: {
      label: 'Queue',
      navigable: true,
      icon: mdiCircleSlice6
    }
  },
  {
    path: '/commit-comparison/:repoID/:hashOne/:hashTwo',
    name: 'commit-comparison',
    component: CommitComparison,
    meta: {
      navigable: false
    }
  },
  {
    path: '/commit-detail/:repoID/:hash',
    name: 'commit-detail',
    component: CommitDetail,
    meta: {
      navigable: false
    }
  },
  {
    path: '/about',
    name: 'about',
    // route level code-splitting
    // this generates a separate chunk (about.[hash].js) for this route
    // which is lazy-loaded when the route is visited.
    component: () =>
      import(/* webpackChunkName: "about" */ '../views/About.vue'),
    meta: {
      label: 'About',
      navigable: true,
      icon: mdiInformationOutline
    }
  }
]

class VueRouterEx extends VueRouter {
  matcher: any

  public routes: RouteConfig[] = []

  constructor(options: RouterOptions) {
    super(options)
    const { addRoutes } = this.matcher
    const { routes } = options

    this.routes = routes!

    this.matcher.addRoutes = (newRoutes: RouteConfig[]) => {
      this.routes.push(...newRoutes)
      addRoutes(newRoutes)
    }
  }
}

Vue.use(VueRouterEx)

const router = new VueRouterEx({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

export default router
