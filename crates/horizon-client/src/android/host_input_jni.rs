//! Host-app controls bridge: JNI natives behind `dev.horizon.android.HorizonInput`.
//!
//! A Java overlay (DroidBridge-style touch controls in a transparent window above the
//! NativeActivity) sends GLFW-style keyboard and mouse input here. Natives only queue
//! events; `game/host_input.rs` drains the queue on the game thread, once per idle tick,
//! so no engine state is touched from the Android UI thread.
use jni::{
    JNIEnv,
    objects::{JClass, JString},
    sys::{jboolean, jfloat, jint, jstring},
};
use std::{
    collections::VecDeque,
    sync::{
        Mutex,
        atomic::{AtomicBool, AtomicI32, Ordering},
    },
};

/// Oldest events are dropped past this, so a stalled game thread cannot grow memory.
const QUEUE_CAP: usize = 4096;

#[derive(Debug, Clone)]
pub enum HostEvent {
    Key { glfw: i32, down: bool },
    Text(String),
    /// Pointer position in Android window pixels.
    Cursor { x: f32, y: f32 },
    /// Moves the pointer baseline without producing motion.
    CursorSilently { x: f32, y: f32 },
    Mouse { glfw_button: i32, down: bool },
    Scroll { x: f32, y: f32 },
}

static QUEUE: Mutex<VecDeque<HostEvent>> = Mutex::new(VecDeque::new());
static GRABBED: AtomicBool = AtomicBool::new(false);
static ATTACHED: AtomicBool = AtomicBool::new(false);
static CONTROLS_ATTACHED: AtomicBool = AtomicBool::new(false);
static IN_GAME: AtomicBool = AtomicBool::new(false);
static KEYBOARD_REQUEST: AtomicBool = AtomicBool::new(false);
static FPS: AtomicI32 = AtomicI32::new(0);
static IME_INSET: AtomicI32 = AtomicI32::new(0);
static CURSOR_SHAPE: AtomicI32 = AtomicI32::new(0);

fn push(event: HostEvent) {
    ATTACHED.store(true, Ordering::Relaxed);
    if let Ok(mut q) = QUEUE.lock() {
        if q.len() >= QUEUE_CAP {
            q.pop_front();
        }
        q.push_back(event);
    }
}

/// True once the host overlay has sent anything, so the engine can hide its own touch HUD.
pub fn attached() -> bool {
    ATTACHED.load(Ordering::Relaxed)
}

/// True while the Java overlay is drawing the touch controls; the engine hides its own HUD.
pub fn controls_attached() -> bool {
    CONTROLS_ATTACHED.load(Ordering::Relaxed)
}

/// Game thread: a world is open (not the launcher, a title screen or a loading screen).
pub fn publish_in_game(in_game: bool) {
    IN_GAME.store(in_game, Ordering::Relaxed);
}

/// The Java overlay is attached and a world is open, so it draws the controls.
pub fn controls_active() -> bool {
    CONTROLS_ATTACHED.load(Ordering::Relaxed) && IN_GAME.load(Ordering::Relaxed)
}

/// The overlay's keyboard button was pressed since the last call.
pub fn take_keyboard_request() -> bool {
    KEYBOARD_REQUEST.swap(false, Ordering::Relaxed)
}

/// Everything queued since the last call, in order.
pub fn drain() -> Vec<HostEvent> {
    QUEUE
        .lock()
        .map(|mut q| q.drain(..).collect())
        .unwrap_or_default()
}

/// Game thread -> Java: whether the pointer is grabbed (no screen open).
pub fn publish_grabbed(grabbed: bool) {
    GRABBED.store(grabbed, Ordering::Relaxed);
}
pub fn publish_fps(fps: f32) {
    FPS.store(fps.round() as i32, Ordering::Relaxed);
}
pub fn publish_cursor_shape(id: i32) {
    CURSOR_SHAPE.store(id, Ordering::Relaxed);
}
/// Physical pixels the finished frame is translated upward while the IME is open.
pub fn ime_viewport_bottom_inset() -> i32 {
    IME_INSET.load(Ordering::Relaxed)
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeKey")]
extern "system" fn native_key(_: JNIEnv, _: JClass, glfw_key: jint, pressed: jboolean) {
    push(HostEvent::Key { glfw: glfw_key, down: pressed != 0 });
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeText")]
extern "system" fn native_text(mut env: JNIEnv, _: JClass, text: JString) {
    if let Ok(s) = env.get_string(&text) {
        let s: String = s.into();
        if !s.is_empty() {
            push(HostEvent::Text(s));
        }
    }
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeCursor")]
extern "system" fn native_cursor(_: JNIEnv, _: JClass, x: jfloat, y: jfloat) {
    push(HostEvent::Cursor { x, y });
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeCursorSilently")]
extern "system" fn native_cursor_silently(_: JNIEnv, _: JClass, x: jfloat, y: jfloat) {
    push(HostEvent::CursorSilently { x, y });
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeMouseButton")]
extern "system" fn native_mouse_button(_: JNIEnv, _: JClass, button: jint, pressed: jboolean) {
    push(HostEvent::Mouse { glfw_button: button, down: pressed != 0 });
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeScroll")]
extern "system" fn native_scroll(_: JNIEnv, _: JClass, x: jfloat, y: jfloat) {
    push(HostEvent::Scroll { x, y });
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeIsGrabbed")]
extern "system" fn native_is_grabbed(_: JNIEnv, _: JClass) -> jboolean {
    GRABBED.load(Ordering::Relaxed) as jboolean
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeSetImeViewportBottomInset")]
extern "system" fn native_set_ime_inset(_: JNIEnv, _: JClass, inset: jint) {
    IME_INSET.store(inset.max(0), Ordering::Relaxed);
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeFps")]
extern "system" fn native_fps(_: JNIEnv, _: JClass) -> jint {
    FPS.load(Ordering::Relaxed)
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeCursorShape")]
extern "system" fn native_cursor_shape(_: JNIEnv, _: JClass) -> jint {
    CURSOR_SHAPE.load(Ordering::Relaxed)
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeGraphicsInfo")]
extern "system" fn native_graphics_info(env: JNIEnv, _: JClass) -> jstring {
    env.new_string("").map_or(std::ptr::null_mut(), |s| s.into_raw())
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeSetControlsAttached")]
extern "system" fn native_set_controls_attached(_: JNIEnv, _: JClass, attached: jboolean) {
    CONTROLS_ATTACHED.store(attached != 0, Ordering::Relaxed);
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeIsInGame")]
extern "system" fn native_is_in_game(_: JNIEnv, _: JClass) -> jboolean {
    IN_GAME.load(Ordering::Relaxed) as jboolean
}

#[unsafe(export_name = "Java_dev_horizon_android_HorizonInput_nativeRequestKeyboard")]
extern "system" fn native_request_keyboard(_: JNIEnv, _: JClass) {
    KEYBOARD_REQUEST.store(true, Ordering::Relaxed);
}
