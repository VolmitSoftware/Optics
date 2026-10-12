const WAIT_MS = 10_000
const SCENES = ['grow-vanish', 'axis-scale', 'continuous-spin', 'eased-motion', 'easing-comparison', 'keyframe-loop', 'boolean-masks', 'shape-morph', 'affine-shear', 'linked-transform', 'mirror-transform', 'masked-crossing']

function track(context) {
  const bot = context.bot
  const state = { context, bot, world: undefined, abilities: undefined, clocks: [], corrections: 0, packets: 0, changes: new Map(), captions: new Set(), removed: new Set(), listeners: [], physics: bot.physicsEnabled }
  const on = (emitter, event, listener) => {
    emitter.on(event, listener)
    state.listeners.push([emitter, event, listener])
  }
  const changed = (x, y, z, id) => {
    state.changes.set(`${x},${y},${z}`, id)
  }
  on(bot._client, 'respawn', (packet) => { state.world = packet.worldState.name })
  on(bot._client, 'abilities', (packet) => { state.abilities = packet.flags })
  on(bot._client, 'position', () => { state.corrections++ })
  on(bot._client, 'update_time', (packet) => { state.clocks = packet.clockUpdates.map((clock) => ({ id: clock.id, ticks: Number(clock.totalTicks), rate: clock.rate })) })
  on(bot._client, 'block_change', (packet) => {
    state.packets++
    changed(packet.location.x, packet.location.y, packet.location.z, packet.type)
  })
  on(bot._client, 'multi_block_change', (packet) => {
    state.packets++
    for (const raw of packet.records) {
      const record = BigInt(raw)
      changed(packet.chunkCoordinates.x * 16 + Number((record >> 8n) & 15n),
        packet.chunkCoordinates.y * 16 + Number(record & 15n),
        packet.chunkCoordinates.z * 16 + Number((record >> 4n) & 15n), Number(record >> 12n))
    }
  })
  on(bot, 'entitySpawn', (entity) => {
    if (entity.name === 'text_display') state.captions.add(entity.id)
  })
  on(bot._client, 'entity_destroy', (packet) => {
    for (const id of packet.entityIds) if (state.captions.has(id)) state.removed.add(id)
  })
  bot.physicsEnabled = false
  return state
}

function release(state) {
  state.bot.clearControlStates()
  state.bot.pathfinder?.setGoal(null)
  state.bot.physicsEnabled = state.physics
  for (const [emitter, event, listener] of state.listeners) emitter.removeListener(event, listener)
}

function snapshotPlayer(state) {
  return { position: state.bot.entity.position.clone(), mode: state.bot.game.gameMode, dimension: state.bot.game.dimension }
}

function lane(state) {
  return Math.round(state.bot.entity.position.x / 96) * 96
}

function captions(state) {
  return Object.values(state.bot.entities).filter((entity) => entity.name === 'text_display')
}

function snapshotBlocks(state, center) {
  const result = new Map()
  const position = state.bot.entity.position.clone()
  for (let x = center - 18; x <= center + 18; x++) {
    for (let y = 63; y <= 76; y++) {
      for (let z = -25; z <= 1; z++) {
        position.set(x, y, z)
        const block = state.bot.blockAt(position)
        if (!block) return undefined
        result.set(`${x},${y},${z}`, block.stateId)
      }
    }
  }
  return result
}

async function blocksReady(context, state, center) {
  return context.waitUntil(() => snapshotBlocks(state, center), { timeoutMs: WAIT_MS, intervalMs: 100, label: `${state.bot.username} loaded projection volume` })
}

function differences(first, next) {
  return [...first].filter(([key, id]) => next.get(key) !== id)
}

async function command(context, action, target = context.bot.username) {
  const response = await context.command(`/opticsdemo ${action} ${target}`, new RegExp(`player=${target} scene=`), WAIT_MS)
  const status = Object.fromEntries([...response.matchAll(/([A-Za-z]+)=([^\s]+)/g)].map((match) => [match[1], match[2]]))
  context.expect(status.player === target && status.scene, 'Malformed demo status', { response })
  context.report.opticsDemo ??= []
  context.report.opticsDemo.push({ actor: target, command: action, status })
  return status
}

function active(context, status, scene) {
  context.expect(status.scene === scene && status.renderer === 'block-packets' && status.ready === 'true', 'Expected active vanilla block-packet session', { status, scene })
  context.expect(Number(status.cameraError) <= 0.01, 'Server camera moved from its fixed pose', { status })
}

async function noCaption(context, world, center) {
  context.expect(/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(world ?? ''), 'Stage dimension identifier was not received in the respawn packet', { world })
  const token = `OpticsCaptionClear_${center}_${Date.now()}`
  await context.command(`/execute in ${world} positioned ${center} 68.7 0 unless entity @e[type=minecraft:text_display,distance=..2] run say ${token}`, token, WAIT_MS)
}

async function restored(context, state, before) {
  await context.waitUntil(() => state.bot.entity.position.distanceTo(before.position) < 0.05 && state.bot.game.gameMode === before.mode && state.bot.game.dimension === before.dimension,
    { timeoutMs: WAIT_MS, label: `${state.bot.username} restored position, dimension and game mode` })
  context.expect(state.abilities !== undefined && (state.abilities & 15) === 0, 'Survival flight and invulnerability flags were not restored', { flags: state.abilities })
  const objective = context.report.opticsClockObjective
  await context.command(`/execute as ${state.bot.username} at @s store result score ${state.bot.username} ${objective} run time query time`, /time|tick|clock/i, WAIT_MS)
  const response = await context.command(`/scoreboard players get ${state.bot.username} ${objective}`, /has \d+ \[/i, WAIT_MS)
  const daytime = Number(response.match(/has (\d+) \[/i)[1]) % 24000
  await context.waitUntil(() => state.clocks.some((clock) => Math.abs(clock.ticks % 24000 - daytime) <= 200), { timeoutMs: WAIT_MS, label: `${state.bot.username} restored protocol time` })
  context.expect(state.clocks.some((clock) => clock.rate > 0), 'Returned player time remained frozen', { clocks: state.clocks, daytime })
}

async function survival(context, state) {
  if (state.bot.game.gameMode !== 'survival') {
    await context.command(`/gamemode survival ${state.bot.username}`, /(?:game mode|gamemode).*survival|survival.*(?:game mode|gamemode)/i, WAIT_MS)
  }
  await context.waitUntil(() => state.bot.game.gameMode === 'survival', { timeoutMs: WAIT_MS, label: `${state.bot.username} survival baseline` })
  return snapshotPlayer(state)
}

async function staticPreview(context, state, scene) {
  const status = await command(context, `preview ${scene}`, state.bot.username)
  active(context, status, scene)
  context.expect(Number(status.tick) === 0 && status.completed === 'false' && Number(status.visible) > 0 && Number(status.packetChanges) > 0, 'Preview did not reset or project blocks', { status })
  await context.waitUntil(() => captions(state).length === 1, { timeoutMs: WAIT_MS, label: `${state.bot.username} one caption` })
  const paused = await blocksReady(context, state, lane(state))
  await context.sleep(350)
  const next = await command(context, 'status', state.bot.username)
  active(context, next, scene)
  context.expect(Number(next.tick) === 0 && next.packetChanges === status.packetChanges, 'Paused preview advanced its timeline or block packets', { status, next })
  context.expect(differences(paused, await blocksReady(context, state, lane(state))).length === 0, 'Paused preview changed protocol block state')
  return paused
}

async function cleanupCommand(state, target) {
  if (state.bot._client.state !== 'play') throw new Error('Primary actor unavailable for cleanup')
  await new Promise((resolve, reject) => {
    const listener = (message) => {
      if (!message.includes(`player=${target} scene=stopped displays=0`)) return
      clearTimeout(timer)
      state.bot.removeListener('messagestr', listener)
      resolve()
    }
    const timer = setTimeout(() => {
      state.bot.removeListener('messagestr', listener)
      reject(new Error(`Cleanup stop for ${target} timed out`))
    }, 3000)
    state.bot.on('messagestr', listener)
    try { state.bot.chat(`/opticsdemo stop ${target}`) }
    catch (error) { clearTimeout(timer); state.bot.removeListener('messagestr', listener); reject(error) }
  })
}

export default {
  name: 'optics-demo-protocol-lifecycle',
  description: 'Standalone OpticsDemo block-packet lifecycle, managed actor isolation, restoration and caption cleanup through the 26.1 protocol.',
  async run(context) {
    context.expect(context.server.host === '127.0.0.1' && context.server.directory, 'Requires an isolated managed loopback instance')
    context.expect(context.bot.version === '26.1', 'Run with --version 26.1 and server-side ViaVersion/ViaBackwards', { version: context.bot.version })
    context.expect(typeof context.connectActor === 'function' && typeof context.reconnectAfter === 'function', 'Managed actor APIs are required')
    const states = []
    const objective = `opclock${Date.now() % 1_000_000_000}`
    context.report.opticsClockObjective = objective
    await context.command(`/scoreboard objectives add ${objective} dummy`, /Created new objective/i, WAIT_MS)
    const primary = track(context)
    states.push(primary)
    let secondary
    let original
    let secondOriginal
    let baseline
    let center
    let secondCenter
    let stageWorld
    let success = false
    let failure
    try {
      await context.step('list all scenes and reject an invalid scene without creating a session', async () => {
        const listed = new Map()
        const listener = (message) => {
          const match = message.match(/^([a-z-]+) \| (\d+) ticks \|/)
          if (match) listed.set(match[1], Number(match[2]))
        }
        primary.bot.on('messagestr', listener)
        try {
          primary.bot.chat('/opticsdemo list')
          await context.waitUntil(() => SCENES.every((scene) => listed.has(scene)), { timeoutMs: WAIT_MS, label: 'all twelve scene listings' })
          context.expect(listed.size === 12 && [...listed.values()].every((ticks) => ticks > 0), 'Scene listing is incomplete or has invalid durations', { listed: [...listed] })
        } finally { primary.bot.removeListener('messagestr', listener) }
        await context.command('/opticsdemo preview invalid-protocol-scene', 'Unknown scene: invalid-protocol-scene', WAIT_MS)
        const status = await command(context, 'status')
        context.expect(status.scene === 'stopped' && status.displays === '0', 'Invalid scene created a session', { status })
      })
      await context.step('setup a survival actor and enforce the fixed camera on a movement attempt', async () => {
        await context.command('/time set 1000', /(?:Set .* tick\(s\)|time.*1000|1000.*time)/i, WAIT_MS)
        await context.waitUntil(() => primary.clocks.some((clock) => clock.rate > 0 && Math.abs(clock.ticks % 24000 - 1000) <= 200), { timeoutMs: WAIT_MS, label: 'running return-world clock baseline' })
        original = await survival(context, primary)
        const status = await command(context, 'setup')
        active(context, status, 'ready')
        context.expect(status.displays === '0' && status.tick === '0', 'Setup created a scene prematurely', { status })
        await context.waitUntil(() => primary.bot.game.gameMode === 'creative' && primary.abilities !== undefined && (primary.abilities & 7) === 7 && primary.world,
          { timeoutMs: WAIT_MS, label: 'creative flying invulnerable stage arrival' })
        await context.waitUntil(() => primary.clocks.some((clock) => clock.ticks % 24000 === 6000 && clock.rate === 0), { timeoutMs: WAIT_MS, label: 'fixed noon protocol time' })
        center = lane(primary)
        stageWorld = primary.world
        baseline = await blocksReady(context, primary, center)
        const camera = primary.bot.entity.position.clone()
        context.expect(Math.abs(camera.x - center) < 0.01 && Math.abs(camera.z - 6) < 0.01 && Math.abs(camera.y - 64.38) < 0.03, 'Unexpected fixed stage camera coordinates', { camera, center })
        const corrected = context.waitForEvent('forcedMove', () => primary.bot.entity.position.distanceTo(camera) < 0.05, WAIT_MS)
        primary.bot._client.write('position', { x: camera.x + 0.75, y: camera.y, z: camera.z, onGround: false, flags: { onGround: false, hasHorizontalCollision: false } })
        await corrected
        const correctionCount = primary.corrections
        await context.sleep(500)
        context.expect(primary.corrections - correctionCount <= 2, 'Fixed-camera acknowledgment caused recurring corrections', { correctionCount, corrections: primary.corrections })
        active(context, await command(context, 'status'), 'ready')
        context.expect(primary.bot.entity.position.distanceTo(camera) < 0.05, 'Client did not receive fixed-position correction')
      })
      await context.step('preview is static and play advances actual projected block packets', async () => {
        const paused = await staticPreview(context, primary, 'continuous-spin')
        context.expect(differences(baseline, paused).length > 0, 'Preview did not change any loaded stage blocks')
        const packetCount = primary.packets
        const started = await command(context, 'play continuous-spin')
        active(context, started, 'continuous-spin')
        await context.waitUntil(async () => {
          const status = await command(context, 'status')
          active(context, status, 'continuous-spin')
          const current = snapshotBlocks(primary, center)
          return Number(status.tick) >= 12 && Number(status.packetChanges) > Number(started.packetChanges) && primary.packets > packetCount && current && differences(paused, current).length > 0
        }, { timeoutMs: WAIT_MS, intervalMs: 250, label: 'animated projection delta and client block state' })
      })
      await context.step('play reaches completion, clears vanished blocks and retains only its caption', async () => {
        const started = await command(context, 'play grow-vanish')
        active(context, started, 'grow-vanish')
        await context.waitUntil(async () => {
          const status = await command(context, 'status')
          active(context, status, 'grow-vanish')
          if (status.completed !== 'true') return false
          context.expect(status.tick === status.durationTicks && status.visible === '0' && status.displays === '1', 'Completed vanish scene retained projected blocks', { status })
          return true
        }, { timeoutMs: 25_000, intervalMs: 500, label: 'grow-vanish completed timeline' })
        context.expect(differences(baseline, await blocksReady(context, primary, center)).length === 0 && captions(primary).length === 1, 'Vanished scene failed to restore client blocks or retain one caption')
      })
      await context.step('two managed players have distinct lanes and private projection sessions', async () => {
        const secondContext = await context.connectActor(primary.bot.username === 'OpticsPeerQA' ? 'OpticsPeerQB' : 'OpticsPeerQA')
        secondary = track(secondContext)
        states.push(secondary)
        await secondContext.command('/opticsdemo list', /permission|Unknown or incomplete command/i, WAIT_MS)
        context.expect((await command(context, 'status', secondary.bot.username)).scene === 'stopped', 'Ordinary actor command created a demo session')
        secondOriginal = await survival(context, secondary)
        active(context, await command(context, 'setup', secondary.bot.username), 'ready')
        await context.waitUntil(() => secondary.world === stageWorld && secondary.bot.game.gameMode === 'creative', { timeoutMs: WAIT_MS, label: 'secondary stage arrival' })
        secondCenter = lane(secondary)
        context.expect(Math.abs(center - secondCenter) >= 96, 'Sessions share a stage lane', { center, secondCenter })
        const secondaryBaseline = await blocksReady(context, secondary, secondCenter)
        await staticPreview(context, primary, 'axis-scale')
        await staticPreview(context, secondary, 'mirror-transform')
        const peerView = await blocksReady(context, secondary, secondCenter)
        context.expect(differences(secondaryBaseline, peerView).length > 0, 'Secondary projection never changed blocks')
        context.expect(differences(baseline, await blocksReady(context, secondary, center)).length === 0, 'Primary fake blocks leaked into the secondary client')
        context.expect(differences(secondaryBaseline, await blocksReady(context, primary, secondCenter)).length === 0, 'Secondary fake blocks leaked into the primary client')
        context.expect(captions(primary).length === 1 && captions(secondary).length === 1, 'Captions are not private per player')
        const primaryCaption = captions(primary)[0].id
        const peerCaption = captions(secondary)[0].id
        context.expect(primaryCaption !== peerCaption && !primary.bot.entities[peerCaption] && !secondary.bot.entities[primaryCaption], 'One player received the other caption entity')
        const frozen = await command(context, 'status', secondary.bot.username)
        await command(context, 'play continuous-spin')
        await context.sleep(700)
        const unchanged = await command(context, 'status', secondary.bot.username)
        context.expect(unchanged.scene === frozen.scene && unchanged.tick === '0' && unchanged.packetChanges === frozen.packetChanges, 'Primary playback changed the secondary session', { frozen, unchanged })
        context.expect(differences(peerView, await blocksReady(context, secondary, secondCenter)).length === 0, 'Primary playback changed peer projection blocks')
      })
      await context.step('stop restores player and projected blocks and removes the caption without stopping the peer', async () => {
        const dirtyKeys = [...primary.changes].filter(([key, id]) => baseline.has(key) && baseline.get(key) !== id).map(([key]) => key)
        context.expect(dirtyKeys.length > 0, 'No projected packet states were available to verify restoration')
        const ownCaption = captions(primary)[0].id
        const stopped = await command(context, 'stop')
        context.expect(stopped.scene === 'stopped' && stopped.displays === '0', 'Stop did not report clean state', { stopped })
        await restored(context, primary, original)
        context.expect(dirtyKeys.every((key) => primary.changes.get(key) === baseline.get(key)), 'Stop omitted original block restoration packets', { unrestored: dirtyKeys.filter((key) => primary.changes.get(key) !== baseline.get(key)).slice(0, 20) })
        context.expect(primary.removed.has(ownCaption) && !primary.bot.entities[ownCaption], 'Stop omitted caption destruction packet')
        await noCaption(context, stageWorld, center)
        active(context, await command(context, 'status', secondary.bot.username), 'mirror-transform')
        context.expect(captions(secondary).length === 1, 'Stopping primary removed the secondary caption')
      })
      await context.step('repeated preview and play reuse one caption and restore on every stop', async () => {
        for (const scene of ['axis-scale', 'continuous-spin', 'affine-shear']) {
          active(context, await command(context, 'setup'), 'ready')
          await blocksReady(context, primary, center)
          await staticPreview(context, primary, scene)
          await staticPreview(context, primary, scene)
          const caption = captions(primary)[0].id
          active(context, await command(context, `play ${scene}`), scene)
          await context.sleep(250)
          active(context, await command(context, `play ${scene}`), scene)
          context.expect(captions(primary).length === 1 && captions(primary)[0].id === caption, 'Repeated scene selection leaked or replaced captions')
          await command(context, 'stop')
          await restored(context, primary, original)
          context.expect(primary.removed.has(caption), 'Repeated stop omitted caption removal')
          await noCaption(context, stageWorld, center)
        }
      })
      await context.step('planned quit cleans the session and reconnect permits a fresh preview', async () => {
        const old = secondary
        const replacement = await old.context.reconnectAfter(() => old.bot.quit('Optics demo planned reconnect'), { timeoutMs: WAIT_MS })
        release(old)
        states.splice(states.indexOf(old), 1)
        secondary = track(replacement)
        states.push(secondary)
        await context.waitUntil(() => secondary.bot.game.gameMode === secondOriginal.mode, { timeoutMs: WAIT_MS, label: 'reconnected actor restored game mode' })
        const status = await command(context, 'status', secondary.bot.username)
        context.expect(status.scene === 'stopped' && status.displays === '0', 'Quit left an active session', { status })
        context.expect(secondary.bot.entity.position.distanceTo(secondOriginal.position) < 0.05 && secondary.bot.game.dimension === secondOriginal.dimension, 'Quit did not save the restored return location', { expected: secondOriginal.position, actual: secondary.bot.entity.position })
        context.expect(captions(secondary).length === 0, 'Reconnect received a stale caption')
        await noCaption(context, stageWorld, secondCenter)
        const reconnectOrigin = snapshotPlayer(secondary)
        active(context, await command(context, 'setup', secondary.bot.username), 'ready')
        await blocksReady(context, secondary, lane(secondary))
        await staticPreview(context, secondary, 'continuous-spin')
        const freshCaption = captions(secondary)[0].id
        const freshCenter = lane(secondary)
        await command(context, 'stop', secondary.bot.username)
        await restored(context, secondary, reconnectOrigin)
        context.expect(secondary.removed.has(freshCaption), 'Post-reconnect stop omitted caption removal')
        await noCaption(context, stageWorld, freshCenter)
        context.expect((await command(context, 'status')).scene === 'stopped', 'Primary session restarted during peer reconnect')
      })
      success = true
    } catch (error) {
      failure = error
    } finally {
      const errors = []
      for (const state of states) {
        try { await cleanupCommand(primary, state.bot.username) }
        catch (error) { errors.push(error) }
      }
      if (success) {
        try {
          await noCaption(context, stageWorld, center)
          await noCaption(context, stageWorld, secondCenter)
        } catch (error) { errors.push(error) }
      }
      try { await context.command(`/scoreboard objectives remove ${objective}`, /Removed objective/i, WAIT_MS) }
      catch (error) { errors.push(error) }
      for (const state of states) release(state)
      if (failure || errors.length) throw new AggregateError([...(failure ? [failure] : []), ...errors], [failure, ...errors].filter(Boolean).map((error) => error.message).join('; '))
    }
  }
}
