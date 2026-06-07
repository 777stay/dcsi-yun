import Vue from 'vue';
import Vuex from 'vuex';

Vue.use(Vuex);

export default new Vuex.Store({
  state: {
    count: 0
  },
  getters: {
    doubleCount: (state) => {
      return state.count * 2; // 计算双倍计数
    }
  },
  mutations: {
    increment(state) {
      state.count++; // 增加计数
    }
  },
  actions: {
    increment({ commit }) {
      commit('increment'); // 触发 mutation
    }
  }
});