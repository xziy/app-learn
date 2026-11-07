import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // The library's scaffold pads for the safe area itself; SwiftUI must not pad it a second time.
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
