const fs = require('fs')
const path = require('path')

const routedViews = [
  'form/index.vue',
  'nested/index.vue',
  'pointCloudMap/index.vue',
  'pointCloudMap/RobotDetailView.vue',
  'multiView/index.vue',
  'potree/indexcopy.vue',
  'fusion/index.vue',
  'robotManager/index.vue',
  'robotManager/kmlIndex.vue',
  'testImage/index.vue',
  'plyDetection/index.vue'
]

describe('deployment URLs', () => {
  test.each(routedViews)('%s does not use a fixed backend host', relativePath => {
    const source = fs.readFileSync(
      path.resolve(__dirname, '../../../src/views', relativePath),
      'utf8'
    )

    expect(source).not.toMatch(/(?:192\.168\.\d+\.\d+|127\.0\.0\.1|localhost):\d+/)
  })
})
