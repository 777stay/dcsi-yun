// src/stores/robot.ts (修正版)

import { defineStore } from 'pinia'
import axios from 'axios'

export interface RobotStatus {
  cpu_percent: number;
  ram_percent: number;
  online: boolean;
  last_update: string;
  current_status_message: string;
}

// 定义一个类型，表示机器人ID到其状态的映射
export type RobotStatuses = {
  [key: string]: RobotStatus;
}

// 为了方便管理，将后端地址定义为一个常量
// const API_BASE_URL = '192.168.194.74:8000'; // 如果后端在另一台机器，请替换为它的IP地址

const API_BASE_URL = '192.168.3.98:8000'; 

export const useRobotStore = defineStore('robot', {
  state: () => ({
    // **核心修改**: 从 Map 改为普通对象
    statuses: {} as RobotStatuses,
    isConnected: false,
    robotConfigs: {} as { [key: string]: { displayName: string } },
  }),
  getters: {
    // **新增**: 一个getter来获取除控制站外的真实机器人列表
    actualRobots: (state) => {
      const robots = { ...state.robotConfigs };
      delete robots.robot; // 移除控制站本身
      return robots;
    }
  },
  actions: {
    async fetchRobotConfigs() {
      try {
        const response = await axios.get(`http://${API_BASE_URL}/api/robots`);
        this.robotConfigs = response.data;
      } catch (error) {
        console.error("Failed to fetch robot configs:", error);
      }
    },

    connectWebSocket() {
      // 确保只连接一次
      if (this.isConnected) return;

      const socket = new WebSocket(`ws://${API_BASE_URL}/ws/status`)

      socket.onopen = () => {
        this.isConnected = true
        console.log('WebSocket connected successfully.');
      }

      socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          // **核心修改**: 直接用接收到的新对象替换整个旧对象，这是最可靠的响应式更新
          this.statuses = data;
        } catch (error) {
          console.error("Failed to parse WebSocket message:", error);
        }
      }

      socket.onclose = () => {
        this.isConnected = false
        // 将所有机器人状态设为离线
        for (const robotId in this.statuses) {
          this.statuses[robotId].online = false;
        }
        console.log('WebSocket disconnected.');
      }

      socket.onerror = (error) => {
        console.error('WebSocket Error:', error);
      }
    },
    async sendCommand(robotId: string, commandName: string) {
      try {
        const response = await axios.post(`http://${API_BASE_URL}/api/command/${robotId}/${commandName}`)
        console.log('Command response:', response.data)
      } catch (error) {
        console.error('Failed to send command:', error)
      }
    },
  },
})
