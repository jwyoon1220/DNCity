package com.atsuishio.superbwarfare.event

import com.atsuishio.superbwarfare.client.MouseMovementHandler
import com.atsuishio.superbwarfare.config.client.ControlConfig
import com.atsuishio.superbwarfare.data.gun.GunData
import com.atsuishio.superbwarfare.data.vehicle.subdata.EngineType
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleEngineUtils.lerpAngle
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils
import com.atsuishio.superbwarfare.init.ModItems
import com.atsuishio.superbwarfare.init.ModMobEffects
import com.atsuishio.superbwarfare.item.gun.GunItem
import com.atsuishio.superbwarfare.network.message.send.MouseMoveMessage
import com.atsuishio.superbwarfare.tools.*
import net.minecraft.client.CameraType
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec2
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

@EventBusSubscriber(Dist.CLIENT)
object ClientMouseHandler {
    @JvmField
    var posO: Vec2 = Vec2(0f, 0f)

    @JvmField
    var posN: Vec2 = Vec2(0f, 0f)

    @JvmField
    var lerpSpeedX: Double = 0.0

    @JvmField
    var lerpSpeedY: Double = 0.0

    @JvmField
    var speedX: Double = 0.0

    @JvmField
    var speedY: Double = 0.0

    @JvmField
    var freeCameraPitch: Double = 0.0

    @JvmField
    var freeCameraYaw: Double = 0.0

    @JvmField
    var custom3pDistance: Double = 0.0

    @JvmField
    var custom3pDistanceLerp: Double = 0.0

    @JvmField
    var mouseXMoveTick: Double = 0.0

    @JvmField
    var mouseYMoveTick: Double = 0.0

    @JvmField
    var lerpNacelleSpeedX: Double = 0.0

    @JvmField
    var lerpNacelleSpeedY: Double = 0.0

    @JvmField
    var nacelleCameraPitch: Double = 0.0

    @JvmField
    var nacelleCameraYaw: Double = 0.0

    /**
     * War Thunder风格Mouse Aim：驾驶固定翼/直升机时鼠标从vanilla视角控制中解放出来，
     * 光标在屏幕上自由移动，光标相对屏幕中心的偏移量替代原来的"鼠标位移量累加"作为
     * 瞄准/操纵杆输入。这个标志同时供[com.atsuishio.superbwarfare.tools.notInGame]
     * 用来判断"鼠标没被抓取"是不是我们自己主动放开的，不要因此误判为菜单/失焦状态。
     */
    @JvmField
    var mouseAimActive = false

    /** 当前准星在GUI坐标系下的位置，供[com.atsuishio.superbwarfare.client.overlay.MouseAimReticleOverlay]绘制 */
    @JvmField
    var reticleGuiX = 0f

    @JvmField
    var reticleGuiY = 0f

    /** 进入飞行载具驾驶位前的视角，下车后用来还原（不覆盖玩家原本的视角偏好） */
    private var savedCameraType: CameraType? = null

    private fun isPilotingFlightVehicle(player: Player): Boolean {
        val vehicle = player.vehicle
        return vehicle is VehicleEntity
                && vehicle.firstPassenger == player
                && (vehicle.vehicleType == VehicleType.AIRPLANE || vehicle.vehicleType == VehicleType.HELICOPTER)
    }

    /**
     * 进入固定翼/直升机驾驶位时自动切到载具背后的第三人称视角(不是正对玩家自己的"自拍"视角)，
     * 下车时还原玩家进入前的视角。独立于下面的Mouse Aim状态机，每tick无条件求值——
     * 不嵌套在任何"仍在驾驶"的分支里，这样离开载具的那一刻(而不是下一次误打误撞的分支)就能触发还原。
     */
    private fun updateFlightCameraPerspective(player: Player) {
        val piloting = isPilotingFlightVehicle(player)

        if (piloting) {
            if (savedCameraType == null) {
                savedCameraType = mc.options.cameraType
                if (mc.options.cameraType != CameraType.THIRD_PERSON_BACK) {
                    mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                }
            }
        } else if (savedCameraType != null) {
            mc.options.cameraType = savedCameraType!!
            savedCameraType = null
        }
    }

    /**
     * Mouse Aim的兜底清理：不管下面具体走哪条分支/是否提前return，只要玩家已经不再满足
     * "驾驶固定翼/直升机"这个前提，就立刻把鼠标抓取/光标显隐还原掉，避免上次的bug——
     * 清理逻辑之前被嵌套在"当前仍是飞行载具"的分支里，玩家一下车该分支就整体跳过，
     * mouseAimActive和鼠标抓取状态因此永远不会被复位。这里独立、无条件地每tick检查一次。
     */
    private fun updateMouseAimLifecycle(player: Player) {
        if (!mouseAimActive) return
        if (!isPilotingFlightVehicle(player)) {
            deactivateMouseAim()
        }
    }

    private fun deactivateMouseAim() {
        if (mc.screen == null) {
            GLFW.glfwSetInputMode(mc.window.window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL)
            mc.mouseHandler.grabMouse()
        }
        mouseAimActive = false
    }

    @SubscribeEvent
    fun handleClientTick(event: ClientTickEvent.Post) {
        val player = localPlayer ?: return

        updateFlightCameraPerspective(player)
        updateMouseAimLifecycle(player)

        posO = posN
        posN = MouseMovementHandler.getMousePos()
        val speed = 256f
        val moveSpeedX = Mth.clamp(posN.x - posO.x, -speed, speed)
        val moveSpeedY = Mth.clamp(posN.y - posO.y, -speed, speed)

        val stack = player.mainHandItem

        if (stack.`is`(ModItems.MONITOR.get()) && stack.getOrCreateTag().getBoolean("Using")
            && stack.getOrCreateTag().getBoolean("Linked")
        ) {
            val drone =
                EntityFindUtil.findDrone(player.level(), stack.getOrCreateTag().getString("LinkedDrone")) ?: return

            speedX = (drone.mouseSensitivity / ClientEventHandler.droneFovLerp) * moveSpeedX
            speedY = (drone.mouseSensitivity / ClientEventHandler.droneFovLerp) * moveSpeedY

            lerpSpeedX = Mth.lerp(0.3, lerpSpeedX, speedX)
            lerpSpeedY = Mth.lerp(0.3, lerpSpeedY, speedY)

            if (notInGame) {
                sendPacketToServer(MouseMoveMessage(0.0, 0.0))
            } else {
                sendPacketToServer(MouseMoveMessage(lerpSpeedX, lerpSpeedY))
            }

            return
        }

        val vehicle = player.vehicle
        if (vehicle is VehicleEntity && (vehicle.vehicleType == VehicleType.AIRPLANE || vehicle.vehicleType == VehicleType.HELICOPTER)) {
            // 第三人称背后视角(下车自动切入)和第一人称都允许Mouse Aim；只排除正对着自己的"自拍"视角
            val mouseAimEligible = vehicle.firstPassenger == player
                    && mc.options.cameraType != CameraType.THIRD_PERSON_FRONT
                    && !ClientEventHandler.isFreeCam(player)
                    && !ClientEventHandler.isNacelleCam(player)
                    && mc.screen == null && mc.overlay == null && mc.isWindowActive
                    && !(vehicle.loiterActive && vehicle.computed().engineType == EngineType.AIRCRAFT)

            if (mouseAimEligible) {
                updateMouseAim(player, vehicle)
                return
            } else if (mouseAimActive) {
                deactivateMouseAim()
            }

            val y = if (ControlConfig.INVERT_AIRCRAFT_CONTROL.get()) -1 else 1
            val sensitivity = vehicle.mouseSensitivity

            speedX = sensitivity * moveSpeedX * (if (ClientEventHandler.zoomVehicle && !ClientEventHandler.isNacelleCam(
                    player
                )
            ) 0.3 else 1.0)
            speedY =
                y * sensitivity * moveSpeedY * (if (ClientEventHandler.zoomVehicle && !ClientEventHandler.isNacelleCam(
                        player
                    )
                ) 0.4 else 1.0)

            mouseXMoveTick = Mth.lerp(0.1, mouseXMoveTick, speedX)
            mouseYMoveTick = Mth.lerp(0.1, mouseYMoveTick, speedY)

            if (vehicle.vehicleType == VehicleType.AIRPLANE) {
                lerpSpeedX = Mth.lerp((0.006 * abs(mouseXMoveTick)).coerceAtLeast(0.12), lerpSpeedX, speedX)
                lerpSpeedY = Mth.lerp((0.005 * abs(mouseYMoveTick)).coerceAtLeast(0.12), lerpSpeedY, speedY)
            } else {
                lerpSpeedX = Mth.lerp((0.0045 * abs(mouseXMoveTick)).coerceAtLeast(0.1), lerpSpeedX, speedX * 0.5)
                lerpSpeedY = Mth.lerp((0.0035 * abs(mouseYMoveTick)).coerceAtLeast(0.1), lerpSpeedY, speedY * 0.5)
            }

            lerpNacelleSpeedX =
                Mth.lerp((0.05 * abs(mouseXMoveTick)).coerceAtLeast(0.13), lerpNacelleSpeedX, speedX * 1.4)
            lerpNacelleSpeedY =
                Mth.lerp((0.05 * abs(mouseYMoveTick)).coerceAtLeast(0.13), lerpNacelleSpeedY, speedY * 1.4)

            // 盘旋模式下禁止操控
            if (vehicle.loiterActive && vehicle.computed().engineType == EngineType.AIRCRAFT) {
                return
            }

            var i = 0.0
            if (vehicle.roll < 0) {
                i = 1.0
            } else if (vehicle.roll > 0) {
                i = -1.0
            }

            if (Mth.abs(vehicle.roll) > 90) {
                i *= (1 - (Mth.abs(vehicle.roll) - 90) / 90)
            }

            if (player != vehicle.firstPassenger) return

            if (notInGame) {
                sendPacketToServer(MouseMoveMessage(0.0, 0.0))
            } else {
                if (!(ClientEventHandler.isFreeCam(player) || ClientEventHandler.isNacelleCam(player))) {
                    if (mc.options.cameraType == CameraType.FIRST_PERSON) {
                        if (vehicle.computed().engineType != EngineType.TOM6) {
                            sendPacketToServer(
                                MouseMoveMessage(
                                    (1 - abs(vehicle.roll) / 90) * lerpSpeedX + (abs(vehicle.roll) / 90) * lerpSpeedY * i,
                                    (1 - abs(vehicle.roll) / 90) * lerpSpeedY + (abs(vehicle.roll) / 90) * lerpSpeedX * if (vehicle.roll < 0) -1.0 else 1.0
                                )
                            )
                        }
                    } else {
                        sendPacketToServer(MouseMoveMessage(lerpSpeedX, lerpSpeedY))
                    }
                } else {
                    sendPacketToServer(MouseMoveMessage(0.0, 0.0))
                }
            }

        }
    }

    private const val MOUSE_AIM_LERP_RATE = 0.18
    private const val CAMERA_LOCK_LERP_RATE = 0.12f

    /** 低于此速度(方块/tick)时，镜头改用机头朝向兜底，避免接近静止/悬停时因速度矢量噪声而抖动 */
    private const val CAMERA_LOCK_MIN_SPEED = 0.08

    /**
     * Mouse Aim核心：释放鼠标抓取，让光标在屏幕上自由移动；光标相对屏幕中心的偏移量
     * (而不再是逐tick的鼠标位移量)成为送往服务端的操纵杆输入，配合[updateCameraLockToTravelDirection]
     * 让镜头改为跟随载具的飞行方向，而不是被鼠标直接转动。
     */
    private fun updateMouseAim(player: Player, vehicle: VehicleEntity) {
        if (!mouseAimActive) {
            mouseAimActive = true
            mc.mouseHandler.releaseMouse()
            val cx = mc.window.screenWidth / 2.0
            val cy = mc.window.screenHeight / 2.0
            GLFW.glfwSetCursorPos(mc.window.window, cx, cy)
        } else if (mc.mouseHandler.isMouseGrabbed) {
            // 理论上不该发生（说明别处又抓取了鼠标），保险起见拉回来
            mc.mouseHandler.releaseMouse()
        }
        // 隐藏系统箭头指针，只显示我们自己画的准星圆环，避免两个指针叠在一起
        GLFW.glfwSetInputMode(mc.window.window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)

        val xArr = doubleArrayOf(0.0)
        val yArr = doubleArrayOf(0.0)
        GLFW.glfwGetCursorPos(mc.window.window, xArr, yArr)

        val screenW = mc.window.screenWidth.toDouble()
        val screenH = mc.window.screenHeight.toDouble()

        // 光标不能跑出可见窗口范围：钳回边界，同时把OS指针本身也拉回来
        val clampedX = Mth.clamp(xArr[0], 0.0, screenW)
        val clampedY = Mth.clamp(yArr[0], 0.0, screenH)
        if (clampedX != xArr[0] || clampedY != yArr[0]) {
            GLFW.glfwSetCursorPos(mc.window.window, clampedX, clampedY)
        }

        val guiScale = mc.window.guiScale
        reticleGuiX = (clampedX / guiScale).toFloat()
        reticleGuiY = (clampedY / guiScale).toFloat()

        val invert = if (ControlConfig.INVERT_AIRCRAFT_CONTROL.get()) -1.0 else 1.0
        val nx = Mth.clamp((clampedX - screenW / 2.0) / (screenW / 2.0), -1.0, 1.0)
        val ny = Mth.clamp((clampedY - screenH / 2.0) / (screenH / 2.0), -1.0, 1.0) * invert

        // 固定速率的lerp，让"教官"的响应平滑跟随光标偏移，而不是瞬间贴到目标偏转量上
        lerpSpeedX = Mth.lerp(MOUSE_AIM_LERP_RATE, lerpSpeedX, nx)
        lerpSpeedY = Mth.lerp(MOUSE_AIM_LERP_RATE, lerpSpeedY, ny)

        updateCameraLockToTravelDirection(player, vehicle)

        if (player != vehicle.firstPassenger || notInGame) {
            sendPacketToServer(MouseMoveMessage(0.0, 0.0))
            return
        }

        var i = 0.0
        if (vehicle.roll < 0) {
            i = 1.0
        } else if (vehicle.roll > 0) {
            i = -1.0
        }
        if (Mth.abs(vehicle.roll) > 90) {
            i *= (1 - (Mth.abs(vehicle.roll) - 90) / 90)
        }

        if (vehicle.computed().engineType != EngineType.TOM6) {
            sendPacketToServer(
                MouseMoveMessage(
                    (1 - abs(vehicle.roll) / 90) * lerpSpeedX + (abs(vehicle.roll) / 90) * lerpSpeedY * i,
                    (1 - abs(vehicle.roll) / 90) * lerpSpeedY + (abs(vehicle.roll) / 90) * lerpSpeedX * if (vehicle.roll < 0) -1.0 else 1.0
                )
            )
        }
    }

    /**
     * 镜头(玩家自身朝向)平滑转向载具的飞行方向：有明显空速时用速度矢量(更War Thunder)，
     * 接近静止/悬停时退化为机头朝向(避免速度矢量在低速时噪声导致镜头乱转)。
     */
    private fun updateCameraLockToTravelDirection(player: Player, vehicle: VehicleEntity) {
        val vel = vehicle.deltaMovement
        val dir = if (vel.lengthSqr() > CAMERA_LOCK_MIN_SPEED * CAMERA_LOCK_MIN_SPEED) {
            vel.normalize()
        } else {
            vehicle.getViewVector(1f)
        }

        val targetYaw = -VehicleVecUtils.getYRotFromVector(dir).toFloat()
        val targetPitch = -VehicleVecUtils.getXRotFromVector(dir).toFloat()

        player.yRot = lerpAngle(player.yRot, targetYaw, CAMERA_LOCK_LERP_RATE)
        player.xRot = lerpAngle(player.xRot, targetPitch, CAMERA_LOCK_LERP_RATE)
        player.setYHeadRot(player.yRot)
        player.setYBodyRot(player.yRot)
    }

    @Suppress("unused")
    @SubscribeEvent
    fun handleClientTick(event: ViewportEvent.ComputeCameraAngles) {
        val player = localPlayer ?: return

        if (notInGame) {
            freeCameraYaw = 0.0
            freeCameraPitch = 0.0
            return
        }

        val times = mc.deltaFrameTime

        freeCameraYaw -= 0.2f * times * lerpSpeedX
        freeCameraPitch += 0.15f * times * lerpSpeedY

        val vehicle = player.vehicle
        val hover = vehicle is VehicleEntity && vehicle.vehicleType == VehicleType.HELICOPTER && vehicle.hoverMode

        if (!ClientEventHandler.isFreeCam(player)) {
            var s = if (mc.options.cameraType == CameraType.FIRST_PERSON) 0.6 else 0.2
            if (hover) {
                s *= 0.5
            }
            freeCameraYaw = Mth.lerp(s * times, freeCameraYaw, 0.0)
            freeCameraPitch = Mth.lerp(s * times, freeCameraPitch, 0.0)
        }

        while (freeCameraYaw > 180F) {
            freeCameraYaw -= 360
        }
        while (freeCameraYaw <= -180F) {
            freeCameraYaw += 360
        }
        while (freeCameraPitch > 180F) {
            freeCameraPitch -= 360
        }
        while (freeCameraPitch <= -180F) {
            freeCameraPitch += 360
        }

        custom3pDistanceLerp = Mth.lerp(times.toDouble(), custom3pDistanceLerp, custom3pDistance)

        if (ClientEventHandler.isNacelleCam(player)) {
            nacelleCameraYaw -= 0.2f * times * lerpNacelleSpeedX
            nacelleCameraPitch += 0.2f * times * lerpNacelleSpeedY
        } else {
            nacelleCameraYaw = 0.0
            nacelleCameraPitch = 0.0
        }

        nacelleCameraPitch = Mth.clamp(nacelleCameraPitch, 0.0, 180.0)

        while (nacelleCameraYaw > 180F) {
            nacelleCameraYaw -= 360
        }
        while (nacelleCameraYaw <= -180F) {
            nacelleCameraYaw += 360
        }
        while (nacelleCameraPitch > 180F) {
            nacelleCameraPitch -= 360
        }
        while (nacelleCameraPitch <= -180F) {
            nacelleCameraPitch += 360
        }
    }

    /**
     * 反转鼠标
     */
    @JvmStatic
    fun invertY(): Int {
        val player = localPlayer ?: return 1
        val vehicle = player.vehicle as? VehicleEntity ?: return 1

        if ((vehicle.vehicleType == VehicleType.AIRPLANE || vehicle.vehicleType == VehicleType.HELICOPTER)
            && vehicle.firstPassenger == player
        ) {
            return if (ControlConfig.INVERT_AIRCRAFT_CONTROL.get()) -1 else 1
        }
        return 1
    }

    @JvmStatic
    fun changeSensitivity(original: Double): Double {
        val player = localPlayer ?: return original
        if (player.hasEffect(ModMobEffects.SHOCK) && !player.isSpectator) {
            return 0.0
        }

        val stack = player.mainHandItem
        val tag = NBTTool.getTag(stack)

        if (stack.`is`(ModItems.MONITOR.get()) && tag.getBoolean("Using") && tag.getBoolean("Linked")) {
            return 0.0
        }

        if (ClientEventHandler.isNacelleCam(player)) {
            return 0.0
        }

        if (player.vehicle is VehicleEntity) {
            val vehicle = player.vehicle as VehicleEntity
            val index = vehicle.getSeatIndex(player)
            if (ClientEventHandler.isFreeCam(player) && vehicle.useAircraftCamera(index)) {
                return 0.0
            }
        }

        if (player.isUsingItem && player.useItem.`is`(ModItems.ARTILLERY_INDICATOR.get()) && mc.options.cameraType == CameraType.FIRST_PERSON) {
            return original / (1 + 0.2 * ClientEventHandler.artilleryIndicatorZoom).coerceAtLeast(0.1)
        }

        val vehicle = player.vehicle
        if (vehicle is VehicleEntity && vehicle.banHand(player)) {
            return vehicle.getSensitivity(
                original,
                ClientEventHandler.zoomVehicle,
                vehicle.getSeatIndex(player),
                vehicle.onGround()
            )
        }

        if (stack.item is GunItem) {
            val data = GunData.from(stack)
            val customSens = data.sensitivity.get()

            if (!player.mainHandItem.isEmpty && mc.options.cameraType == CameraType.FIRST_PERSON) {
                return original / (1 + (0.2 * (data.zoom() - (0.3 * customSens)) * ClientEventHandler.zoomTime))
                    .coerceAtLeast(0.1) * (ControlConfig.MOUSE_SENSITIVITY.get() / 100f)
            }
        }

        return original
    }

//    @SubscribeEvent
//    fun handlePlayerTurn(event: CalculatePlayerTurnEvent) {
//        event.mouseSensitivity = changeSensitivity(event.mouseSensitivity)
//        Minecraft.getInstance().player?.displayClientMessage(Component.literal(event.mouseSensitivity.toString()), true)
//    }
}