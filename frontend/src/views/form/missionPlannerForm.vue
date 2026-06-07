<template>
  <el-row style="margin-top:5px ;"
          v-for="(item, index) in formFields"
          :key="index"
  >
        <el-col :span="5" style="margin:4px;font-size:12px;">{{ item.label }}</el-col>
        <el-col :span="17">
          <component
              :is="item.component"
              v-model="form[item.field]"
              v-bind="item.props"
              v-if="!item.condition || item.condition()"
              style="font-size:12px;margin-left:5px;"
          >
            <el-option
              v-if="item.component === 'el-select'"
              v-for="option in item.props.options"
              :key="option.value"
              :label="option.value"
            ></el-option>
            <el-radio
                v-if="item.component === 'el-radio-group'"
                v-for="option in item.props.options"
                :key="option.value"
                :label="option.value"
            >
              {{ option.label }}
            </el-radio>
          </component>

          <div v-if="item.component === 'el-text'" style="margin: 0;font-size: 14px;">
            <ul style="list-style-type: disc; margin: 0; padding-left: 1px;list-style-position: inside;">
              <li>位置1: {{ props.location1 }}</li>
              <li>位置2: {{ props.location2 }}</li>
              <li>位置3: {{ props.location3 }}</li>
            </ul>
          </div>

        </el-col>
      </el-row>

    <el-row style="margin-top:10px;justify-content: center;">
      <el-button  type="primary" @click="startMissionPlanner">开始任务规划</el-button>
    </el-row>
</template>

<script setup lang="ts">
import {ref, reactive, defineProps, computed} from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from "axios";

// 定义子组件的 props，用于接收父组件的值
const props = defineProps<{
  location1: string;
  location2: string;
  location3: string;
}>();

const form = reactive({
  selected_uav: '选项1',
  plan_mode: 2,
  number_device: 3,
  scan_density: '20',
  drone_speed: '10',
  drone_start: '0',
  drone_end: '10',
  pathsStrictlyInPoly: true,
  location: computed(() => `位置1: ${props.location1}, 位置2: ${props.location2}, 位置3: ${props.location3}`), // 拼接值
  location1:props.location1,
  location2:props.location2,
  location3:props.location3,
  Distribution_ratio1: 20,
  Distribution_ratio2: 50,
  Distribution_ratio3: 30,
});

const formFields = ref([
  { label: '请选择无人机:', field: 'selected_uav', component: 'el-select', props: { placeholder: '请选择无人机', style: 'width:100%',
                                                                                  options: [{value: '选项1',label: '无人机1'},
                                                                                            {value: '选项2',label: '无人机2'},
                                                                                            {value: '选项3',label: '无人机3'}]  }
  },
  { label: '航线规划模式:', field: 'plan_mode', component: 'el-radio-group', props: { options: [{ label: '区域模式', value: 1 }, { label: '线路模式', value: 2 }] } },
  { label: '无人设备数量:', field: 'number_device', component: 'el-input-number', props: { min: 1, max: 8, style: 'width:100%', placeholder: '请输入内容' } },
  { label: '扫描密度：', field: 'scan_density', component: 'el-input', props: { placeholder: '请输入内容', disabled: true } },
  { label: '飞行速度：', field: 'drone_speed', component: 'el-input', props: { placeholder: '请输入内容' } },
  { label: '起飞点：', field: 'drone_start', component: 'el-input', props: { placeholder: '请输入内容' } },
  { label: '终点：', field: 'drone_end', component: 'el-input', props: { placeholder: '请输入内容' } },
  { label: '路线严格控制在任务区内:', field: 'pathsStrictlyInPoly', component: 'el-input', props: { placeholder: '请输入内容' } },
  { label: '初始位置:', field: 'location', component: 'el-text', props: { }},
  { label: '1号分配比例:', field: 'Distribution_ratio1', component: 'el-slider', props: { showInput: true, max: 100 }, condition: () => form.number_device > 0 },
  { label: '2号分配比例:', field: 'Distribution_ratio2', component: 'el-slider', props: { showInput: true, max: 100 }, condition: () => form.number_device > 1 },
  { label: '3号分配比例:', field: 'Distribution_ratio3', component: 'el-slider', props: { showInput: true, max: 100 }, condition: () => form.number_device > 2 },
]);
console.log(form.number_device);

const startMissionPlanner =async () => {
  // 定义设备数和分配比例
  const number_device = ref(form.number_device); // 当前设备数
  const distributionRatios = ref([form.Distribution_ratio1,form.Distribution_ratio2,form.Distribution_ratio3]); // 分配比例数组

  // 检查分配总和
  const total = distributionRatios.value
      .slice(0, number_device.value)
      .reduce((sum, val) => sum + val, 0);

  if (total !== 100) {
    ElMessageBox.alert('分配总和要等于100', '提示', {
      type: 'warning',
      confirmButtonText: '好的',
    })
  }

  const Mission_result_polyline_one = ref(null);
  const Mission_result_polyline_two = ref(null);
  const Mission_result_polyline_three = ref(null);
  try {
    const res = await axios.post("/start_mission_planner", {
      number_device: form.number_device,
      scan_density: form.scan_density,
      plan_mode: form.plan_mode,
      drone_start: form.drone_start,
      drone_end: form.drone_end,
      drone_speed: form.drone_speed,
      pathsStrictlyInPoly: form.pathsStrictlyInPoly,
      location1: form.location1,
      location2: form.location2,
      location3: form.location3,
      Distribution_ratio1: form.Distribution_ratio1,
      Distribution_ratio2: form.Distribution_ratio2,
      Distribution_ratio3: form.Distribution_ratio3
    });

    if (Mission_result_polyline_one.value) {
      Mission_result_polyline_one.value.remove();
    }
    if (Mission_result_polyline_two.value) {
      Mission_result_polyline_two.value.remove();
    }
    if (Mission_result_polyline_three.value) {
      Mission_result_polyline_three.value.remove();
    }

    const colors = ['red', 'blue', 'yellow', '#efc452', '#00F5FF', '#00FF00', 'FF6A6A', '8B7E66', '#FFA54F'];

    console.log(res.data);
    // res.forEach((route_arr, index_x) => {
    //   let points = [];
    //   route_arr.forEach((point_arr) => {
    //     points.push([point_arr[0], point_arr[1]]);
    //   });
    //
    //   // 创建并添加折线
    //   const polyline = L.polyline(points, {
    //     color: colors[index_x] || '#000000', // 默认颜色为黑色
    //   }).addTo(map.value);
    //
    //   if (index_x === 0) {
    //     Mission_result_polyline_one.value = polyline;
    //   } else if (index_x === 1) {
    //     Mission_result_polyline_two.value = polyline;
    //   } else {
    //     Mission_result_polyline_three.value = polyline;
    //   }
    // });
  } catch (error) {
    console.error('请求失败:', error);
  }


};



</script>
<style scoped>

</style>
