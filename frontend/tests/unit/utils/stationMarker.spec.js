import {
  getStationPacketKey,
  isStationGnssPacket,
  normalizeStationPacket
} from '@/utils/stationMarker'

describe('stationMarker utils', () => {
  it('recognizes station gnss packets from backend websocket payloads', () => {
    const packet = {
      type: 'PACKET_ODOM',
      sender: 'robot_station',
      frame: 'mesh_station',
      odom: { x: 114.123456, y: 30.654321 }
    }

    expect(isStationGnssPacket(packet)).toBe(true)
    expect(normalizeStationPacket(packet)).toEqual({
      lon: 114.123456,
      lat: 30.654321
    })
  })

  it('recognizes station gnss packets that use ros-style field names', () => {
    const packet = {
      type: 'PACKET_ODOM',
      sensor_id: 'robot_station',
      frame_id: 'mesh_station',
      odom: { x: 114.1234567, y: 30.6543217 }
    }

    expect(isStationGnssPacket(packet)).toBe(true)
    expect(getStationPacketKey(packet)).toBe('114.123457,30.654322')
  })

  it('rejects non-station or invalid coordinate packets', () => {
    expect(isStationGnssPacket({
      type: 'PACKET_ODOM',
      sender: 'robot_01',
      frame: 'gnss',
      odom: { x: 114, y: 30 }
    })).toBe(false)

    expect(normalizeStationPacket({
      type: 'PACKET_ODOM',
      sender: 'robot_station',
      frame: 'mesh_station',
      odom: { x: 'bad', y: 30 }
    })).toBe(null)
  })
})
