const STATION_SENSOR_ID = 'robot_station'
const STATION_FRAME_ID = 'mesh_station'
const STATION_PACKET_TYPE = 'PACKET_ODOM'

function getPacketSensorId(packet) {
  return packet && (packet.sender || packet.sensor_id || packet.sensorId)
}

function getPacketFrameId(packet) {
  return packet && (packet.frame || packet.frame_id || packet.frameId)
}

function normalizeStationPacket(packet) {
  const odom = packet && packet.odom
  if (!odom) return null

  const lon = Number(odom.x)
  const lat = Number(odom.y)
  if (!Number.isFinite(lon) || !Number.isFinite(lat)) return null

  return { lon, lat }
}

function isStationGnssPacket(packet) {
  return Boolean(
    packet &&
    packet.type === STATION_PACKET_TYPE &&
    getPacketSensorId(packet) === STATION_SENSOR_ID &&
    getPacketFrameId(packet) === STATION_FRAME_ID &&
    normalizeStationPacket(packet)
  )
}

function getStationPacketKey(packet) {
  const position = normalizeStationPacket(packet)
  if (!position) return ''

  return `${position.lon.toFixed(6)},${position.lat.toFixed(6)}`
}

module.exports = {
  STATION_FRAME_ID,
  STATION_SENSOR_ID,
  getStationPacketKey,
  isStationGnssPacket,
  normalizeStationPacket
}
