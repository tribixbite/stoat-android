import { defineConfig } from "astro/config"
import starlight from "@astrojs/starlight"
import markdownIntegration from "@astropub/md"
import starlightThemeNext from 'starlight-theme-next'

// https://astro.build/config
export default defineConfig({
    site: "https://tribixbite.github.io",
    base: "/stoat-android",
    integrations: [
        starlight({
            plugins: [starlightThemeNext()],
            title: "Stoat for Android Technical Documentation",
            social: [
                { icon: "github", label: 'GitHub', href: "https://github.com/tribixbite/stoat-android" },
            ],
            sidebar: [
                {
                    label: "Contributing",
                    items: [
                        // Each item here is one entry in the navigation menu.
                        {
                            label: "Guidelines",
                            link: "/contributing/guidelines",
                        },
                        {
                            label: "Setup",
                            link: "/contributing/setup",
                        }
                    ],
                },
                {
                    label: "Reference",
                    autogenerate: { directory: "reference" },
                },
                {
                    label: "Legal",
                    items: [
                        { label: "Privacy Policy", link: "/legal/privacy-policy" },
                        { label: "Terms of Service", link: "/legal/terms-of-service" },
                    ],
                },
            ],
            customCss: ["./src/styles/custom.css"],
        }),
        markdownIntegration(),
    ],
})
