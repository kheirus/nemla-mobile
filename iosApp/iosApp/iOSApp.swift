import Shared
import SwiftUI

@main
struct iOSApp: App {
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
