//! Host-overlay input: drains `host_input_jni`'s queue into the same window events a
//! keyboard and mouse would make, so every existing key, screen and grab rule applies.
use super::*;
use crate::host_input_jni::{self, HostEvent};
use winit::{
    dpi::PhysicalPosition,
    event::{
        DeviceId, ElementState, KeyEvent, MouseButton, MouseScrollDelta, TouchPhase, WindowEvent,
    },
    keyboard::{Key, KeyCode, NamedKey, NativeKeyCode, PhysicalKey, SmolStr},
};

const LETTERS: [KeyCode; 26] = [
    KeyCode::KeyA, KeyCode::KeyB, KeyCode::KeyC, KeyCode::KeyD, KeyCode::KeyE, KeyCode::KeyF,
    KeyCode::KeyG, KeyCode::KeyH, KeyCode::KeyI, KeyCode::KeyJ, KeyCode::KeyK, KeyCode::KeyL,
    KeyCode::KeyM, KeyCode::KeyN, KeyCode::KeyO, KeyCode::KeyP, KeyCode::KeyQ, KeyCode::KeyR,
    KeyCode::KeyS, KeyCode::KeyT, KeyCode::KeyU, KeyCode::KeyV, KeyCode::KeyW, KeyCode::KeyX,
    KeyCode::KeyY, KeyCode::KeyZ,
];
const DIGITS: [KeyCode; 10] = [
    KeyCode::Digit0, KeyCode::Digit1, KeyCode::Digit2, KeyCode::Digit3, KeyCode::Digit4,
    KeyCode::Digit5, KeyCode::Digit6, KeyCode::Digit7, KeyCode::Digit8, KeyCode::Digit9,
];
const FUNCTION: [KeyCode; 12] = [
    KeyCode::F1, KeyCode::F2, KeyCode::F3, KeyCode::F4, KeyCode::F5, KeyCode::F6,
    KeyCode::F7, KeyCode::F8, KeyCode::F9, KeyCode::F10, KeyCode::F11, KeyCode::F12,
];

/// A GLFW key code (glfw3.h) as the physical and logical keys winit would report.
fn glfw_key(glfw: i32) -> Option<(KeyCode, Key, Option<&'static str>)> {
    let named = |code, key| Some((code, Key::Named(key), None));
    match glfw {
        32 => Some((KeyCode::Space, Key::Named(NamedKey::Space), Some(" "))),
        39 => Some((KeyCode::Quote, Key::Character("'".into()), Some("'"))),
        44 => Some((KeyCode::Comma, Key::Character(",".into()), Some(","))),
        45 => Some((KeyCode::Minus, Key::Character("-".into()), Some("-"))),
        46 => Some((KeyCode::Period, Key::Character(".".into()), Some("."))),
        47 => Some((KeyCode::Slash, Key::Character("/".into()), Some("/"))),
        59 => Some((KeyCode::Semicolon, Key::Character(";".into()), Some(";"))),
        61 => Some((KeyCode::Equal, Key::Character("=".into()), Some("="))),
        91 => Some((KeyCode::BracketLeft, Key::Character("[".into()), Some("["))),
        92 => Some((KeyCode::Backslash, Key::Character("\\".into()), Some("\\"))),
        93 => Some((KeyCode::BracketRight, Key::Character("]".into()), Some("]"))),
        96 => Some((KeyCode::Backquote, Key::Character("`".into()), Some("`"))),
        48..=57 => {
            let d = (glfw - 48) as usize;
            let s = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"][d];
            Some((DIGITS[d], Key::Character(s.into()), Some(s)))
        }
        65..=90 => {
            let i = (glfw - 65) as usize;
            let s = "abcdefghijklmnopqrstuvwxyz";
            let c = &s[i..=i];
            Some((LETTERS[i], Key::Character(c.into()), Some(c)))
        }
        256 => named(KeyCode::Escape, NamedKey::Escape),
        257 => Some((KeyCode::Enter, Key::Named(NamedKey::Enter), None)),
        258 => named(KeyCode::Tab, NamedKey::Tab),
        259 => named(KeyCode::Backspace, NamedKey::Backspace),
        260 => named(KeyCode::Insert, NamedKey::Insert),
        261 => named(KeyCode::Delete, NamedKey::Delete),
        262 => named(KeyCode::ArrowRight, NamedKey::ArrowRight),
        263 => named(KeyCode::ArrowLeft, NamedKey::ArrowLeft),
        264 => named(KeyCode::ArrowDown, NamedKey::ArrowDown),
        265 => named(KeyCode::ArrowUp, NamedKey::ArrowUp),
        266 => named(KeyCode::PageUp, NamedKey::PageUp),
        267 => named(KeyCode::PageDown, NamedKey::PageDown),
        268 => named(KeyCode::Home, NamedKey::Home),
        269 => named(KeyCode::End, NamedKey::End),
        280 => named(KeyCode::CapsLock, NamedKey::CapsLock),
        290..=301 => {
            let i = (glfw - 290) as usize;
            let key = [
                NamedKey::F1, NamedKey::F2, NamedKey::F3, NamedKey::F4, NamedKey::F5,
                NamedKey::F6, NamedKey::F7, NamedKey::F8, NamedKey::F9, NamedKey::F10,
                NamedKey::F11, NamedKey::F12,
            ][i];
            named(FUNCTION[i], key)
        }
        340 => named(KeyCode::ShiftLeft, NamedKey::Shift),
        341 => named(KeyCode::ControlLeft, NamedKey::Control),
        342 => named(KeyCode::AltLeft, NamedKey::Alt),
        343 => named(KeyCode::SuperLeft, NamedKey::Super),
        344 => named(KeyCode::ShiftRight, NamedKey::Shift),
        345 => named(KeyCode::ControlRight, NamedKey::Control),
        346 => named(KeyCode::AltRight, NamedKey::Alt),
        _ => None,
    }
}

fn glfw_mouse(button: i32) -> Option<MouseButton> {
    match button {
        0 => Some(MouseButton::Left),
        1 => Some(MouseButton::Right),
        2 => Some(MouseButton::Middle),
        _ => None,
    }
}

fn state(down: bool) -> ElementState {
    if down { ElementState::Pressed } else { ElementState::Released }
}

impl Game<'_> {
    /// Called from `idle()`. Replays the host overlay's input as window events and reports
    /// grab state, frame rate and cursor shape back to it.
    pub(super) fn android_drain_host_input(&mut self, events: &dyn super::event_loop::EventLoopCtl) {
        host_input_jni::publish_grabbed(self.captured && self.screen == Screen::Playing);
        host_input_jni::publish_fps(self.fps);
        let queued = host_input_jni::drain();
        if queued.is_empty() {
            return;
        }
        let id = self.window.as_ref().map_or(WindowId::dummy(), |w| w.id());
        let device_id = DeviceId::dummy();
        for event in queued {
            match event {
                HostEvent::Key { glfw, down } => {
                    let Some((code, logical, text)) = glfw_key(glfw) else { continue };
                    let text = if down { text.map(SmolStr::new) } else { None };
                    self.handle_window_event(
                        events,
                        id,
                        WindowEvent::KeyboardInput {
                            device_id,
                            event: KeyEvent::host(PhysicalKey::Code(code), logical, text, state(down)),
                            is_synthetic: false,
                        },
                    );
                }
                HostEvent::Text(text) => {
                    for ch in text.chars() {
                        let s = SmolStr::new(ch.to_string());
                        for down in [true, false] {
                            self.handle_window_event(
                                events,
                                id,
                                WindowEvent::KeyboardInput {
                                    device_id,
                                    event: KeyEvent::host(
                                        PhysicalKey::Unidentified(NativeKeyCode::Unidentified),
                                        Key::Character(s.clone()),
                                        down.then(|| s.clone()),
                                        state(down),
                                    ),
                                    is_synthetic: false,
                                },
                            );
                        }
                    }
                }
                HostEvent::CursorSilently { x, y } => {
                    self.host_cursor = Some([x, y]);
                }
                HostEvent::Cursor { x, y } => {
                    let last = self.host_cursor.replace([x, y]);
                    let playing = self.pending_save.is_none()
                        && self.captured
                        && self.focused
                        && self.screen == Screen::Playing;
                    if playing {
                        // Grabbed: pointer motion is camera look, as DeviceEvent::MouseMotion.
                        if let Some([lx, ly]) = last {
                            self.mouse.on_move(0.0, 0.0, f64::from(x - lx), f64::from(y - ly), true, true);
                            self.last_input = Instant::now();
                        }
                    } else {
                        self.handle_window_event(
                            events,
                            id,
                            WindowEvent::CursorMoved {
                                device_id,
                                position: PhysicalPosition::new(f64::from(x), f64::from(y)),
                            },
                        );
                    }
                }
                HostEvent::Mouse { glfw_button, down } => {
                    if let Some(button) = glfw_mouse(glfw_button) {
                        self.handle_window_event(
                            events,
                            id,
                            WindowEvent::MouseInput { device_id, state: state(down), button },
                        );
                        // The touch path opens the soft keyboard after a tap on a text field;
                        // a host click needs the same.
                        if glfw_button == 0
                            && !down
                            && !self.captured
                            && let (Some([x, y]), Some(b)) = (self.host_cursor, self.gui_scale())
                            && (crate::platform::field_at([x / b.scale, y / b.scale])
                                || self.sign_edit.is_some())
                        {
                            self.android_show_keyboard();
                        }
                    }
                }
                HostEvent::Scroll { x, y } => {
                    self.handle_window_event(
                        events,
                        id,
                        WindowEvent::MouseWheel {
                            device_id,
                            delta: MouseScrollDelta::LineDelta(x, y),
                            phase: TouchPhase::Moved,
                        },
                    );
                }
            }
        }
    }
}
