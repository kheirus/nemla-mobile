import Shared
import SwiftUI
import UIKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        let info = Bundle.main.infoDictionary ?? [:]
        #if DEBUG
        let isDebug = true
        #else
        let isDebug = false
        #endif
        MainViewControllerKt.startNemla(
            supabaseUrl: info["SUPABASE_URL"] as? String ?? "",
            supabasePublishableKey: info["SUPABASE_PUBLISHABLE_KEY"] as? String ?? "",
            apiBaseUrl: info["API_BASE_URL"] as? String ?? "",
            isDebug: isDebug
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        MainViewControllerKt.landscapeAllowed() ? .allButUpsideDown : .portrait
    }
}
