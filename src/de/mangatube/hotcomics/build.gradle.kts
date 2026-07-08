plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HotComics.io"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    source {
        lang = "de"
        baseUrl = "https://hotcomics.io"
    }
}
