package com.antodippo.ccmusicsearch

import com.antodippo.ccmusicsearch.apiservices.*
import com.antodippo.ccmusicsearch.infra.PrintDuration
import java.net.URI
import java.time.LocalDate

data class SearchResult(
    val author: String,
    val title: String,
    val duration: Int,
    val bpm: Int,
    val tags: String,
    val date: LocalDate,
    val externalLink: URI,
    val license: CCLicense,
    val licenseUrl: URI = license.toLicenseUrl(),
    val licenseLogoUrl: URI = license.toLicenseLogoUrl(),
    val service: SearchService,
)

enum class SearchService {
    JAMENDO, CCMIXTER, ICONS8, INTERNETARCHIVE, FREESOUND;

    fun toService(): APIService {
        return when (this) {
            JAMENDO -> Jamendo(ApiClientViaHttp(), PrintDuration())
            CCMIXTER -> CCMixter(ApiClientViaHttp(), PrintDuration())
            ICONS8 -> Icons8(ApiClientViaHttp(), PrintDuration())
            INTERNETARCHIVE -> InternetArchive(ApiClientViaHttp(), PrintDuration())
            FREESOUND -> Freesound(ApiClientViaHttp(), PrintDuration())
        }
    }
}

enum class CCLicense {
    CC0, CC_BY, CC_BY_SA, CC_BY_ND, CC_BY_NC, CC_BY_NC_SA, CC_BY_NC_ND, PUBLIC_DOMAIN, UNKNOWN;

    fun toLicenseUrl(): URI {
        return when (this) {
            CC0 -> URI.create("https://creativecommons.org/publicdomain/zero/1.0/")
            CC_BY -> URI.create("https://creativecommons.org/licenses/by/4.0/")
            CC_BY_SA -> URI.create("https://creativecommons.org/licenses/by-sa/4.0/")
            CC_BY_ND -> URI.create("https://creativecommons.org/licenses/by-nd/4.0/")
            CC_BY_NC -> URI.create("https://creativecommons.org/licenses/by-nc/4.0/")
            CC_BY_NC_SA -> URI.create("https://creativecommons.org/licenses/by-nc-sa/4.0/")
            CC_BY_NC_ND -> URI.create("https://creativecommons.org/licenses/by-nc-nd/4.0/")
            PUBLIC_DOMAIN -> URI.create("https://creativecommons.org/publicdomain/mark/1.0/")
            UNKNOWN -> URI.create("https://creativecommons.org/licenses/")
        }
    }

    fun toLicenseLogoUrl(): URI {
        return when (this) {
            CC0 -> URI.create("https://licensebuttons.net/l/zero/1.0/88x31.png")
            CC_BY -> URI.create("https://licensebuttons.net/l/by/4.0/88x31.png")
            CC_BY_SA -> URI.create("https://licensebuttons.net/l/by-sa/4.0/88x31.png")
            CC_BY_ND -> URI.create("https://licensebuttons.net/l/by-nd/4.0/88x31.png")
            CC_BY_NC -> URI.create("https://licensebuttons.net/l/by-nc/4.0/88x31.png")
            CC_BY_NC_SA -> URI.create("https://licensebuttons.net/l/by-nc-sa/4.0/88x31.png")
            CC_BY_NC_ND -> URI.create("https://licensebuttons.net/l/by-nc-nd/4.0/88x31.png")
            PUBLIC_DOMAIN -> URI.create("https://licensebuttons.net/p/mark/1.0/88x31.png")
            UNKNOWN -> URI.create("https://mirrors.creativecommons.org/presskit/cc.primary.srr.gif")
        }
    }

    companion object {
        fun fromUrl(url: String): CCLicense {
            return when {
                url.contains("/zero/") -> CC0
                url.contains("/by/") -> CC_BY
                url.contains("/by-sa/") -> CC_BY_SA
                url.contains("/by-nd/") -> CC_BY_ND
                url.contains("/by-nc/") -> CC_BY_NC
                url.contains("/by-nc-sa/") -> CC_BY_NC_SA
                url.contains("/by-nc-nd/") -> CC_BY_NC_ND
                url.contains("/publicdomain/") -> PUBLIC_DOMAIN
                else -> UNKNOWN
            }
        }
    }
}