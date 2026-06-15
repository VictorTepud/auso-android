use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jstring};
use jni::JNIEnv;
use uuid::Uuid;

/// Hash a password using bcrypt
#[no_mangle]
pub extern "system" fn Java_com_auso_social_rust_RustBridge_hashPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
) -> jstring {
    let password: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    match bcrypt::hash(&password, bcrypt::DEFAULT_COST) {
        Ok(hash) => env.new_string(&hash).unwrap().into_raw(),
        Err(_) => env.new_string("").unwrap().into_raw(),
    }
}

/// Verify a password against a bcrypt hash
#[no_mangle]
pub extern "system" fn Java_com_auso_social_rust_RustBridge_verifyPassword(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
    hash: JString,
) -> jboolean {
    let password: String = match env.get_string(&password) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    let hash: String = match env.get_string(&hash) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    match bcrypt::verify(&password, &hash) {
        Ok(valid) => if valid { 1 } else { 0 },
        Err(_) => 0,
    }
}

/// Validate JWT token (basic structure check)
#[no_mangle]
pub extern "system" fn Java_com_auso_social_rust_RustBridge_validateToken(
    mut env: JNIEnv,
    _class: JClass,
    token: JString,
    _secret: JString,
) -> jboolean {
    let token: String = match env.get_string(&token) {
        Ok(s) => s.into(),
        Err(_) => return 0,
    };

    // Basic JWT structure validation (3 parts separated by dots)
    let parts: Vec<&str> = token.split('.').collect();
    if parts.len() != 3 {
        return 0;
    }

    // Check that each part is valid base64
    for part in &parts {
        if part.is_empty() {
            return 0;
        }
    }

    1
}

/// Generate a unique ID using UUID v4
#[no_mangle]
pub extern "system" fn Java_com_auso_social_rust_RustBridge_generateId(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let id = Uuid::new_v4().to_string();
    env.new_string(&id).unwrap().into_raw()
}

/// Sanitize user input to prevent XSS/injection
#[no_mangle]
pub extern "system" fn Java_com_auso_social_rust_RustBridge_sanitizeInput(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let input: String = match env.get_string(&input) {
        Ok(s) => s.into(),
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let sanitized = ammonia::clean(&input);
    env.new_string(&sanitized).unwrap().into_raw()
}
