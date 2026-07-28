#include <jni.h>
#include <string>
#include <winrt/Windows.Data.Xml.Dom.h>
#include <winrt/Windows.UI.Notifications.h>
#include <winrt/base.h>

using namespace winrt;
using namespace Windows::Data::Xml::Dom;
using namespace Windows::UI::Notifications;

namespace {
constexpr wchar_t APP_ID[] = L"com.shale.desktop.Shale";
constexpr jint OK = 0, UNSUPPORTED = 1, FAILED = 2;
thread_local bool initialized = false;

std::wstring wide(JNIEnv* env, jstring value) {
    if (!value) return {};
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (!chars) return {};
    std::wstring result(reinterpret_cast<const wchar_t*>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return result;
}

void appendText(XmlDocument const& document, XmlElement const& binding, wchar_t const* text) {
    auto element = document.CreateElement(L"text");
    element.AppendChild(document.CreateTextNode(text));
    binding.AppendChild(element);
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_shale_desktop_notification_JniWindowsNotificationBridge_initialize(JNIEnv* env, jobject, jstring expected) noexcept {
#if !defined(_M_X64)
    return UNSUPPORTED;
#else
    try {
        if (wide(env, expected) != APP_ID) return UNSUPPORTED;
        init_apartment(apartment_type::multi_threaded);
        ToastNotificationManager::CreateToastNotifier(APP_ID);
        initialized = true;
        return OK;
    } catch (...) { return UNSUPPORTED; }
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_shale_desktop_notification_JniWindowsNotificationBridge_show(JNIEnv* env, jobject, jstring heading, jstring message) noexcept {
    try {
        if (!initialized) return UNSUPPORTED;
        std::wstring safeHeading = wide(env, heading), safeMessage = wide(env, message);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return FAILED; }
        XmlDocument document;
        auto toast = document.CreateElement(L"toast");
        document.AppendChild(toast);
        auto visual = document.CreateElement(L"visual");
        toast.AppendChild(visual);
        auto binding = document.CreateElement(L"binding");
        binding.SetAttribute(L"template", L"ToastGeneric");
        visual.AppendChild(binding);
        appendText(document, binding, safeHeading.c_str());
        appendText(document, binding, safeMessage.c_str());
        ToastNotificationManager::CreateToastNotifier(APP_ID).Show(ToastNotification(document));
        return OK;
    } catch (...) { return FAILED; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_shale_desktop_notification_JniWindowsNotificationBridge_close(JNIEnv*, jobject) noexcept {
    if (initialized) { uninit_apartment(); initialized = false; }
}
