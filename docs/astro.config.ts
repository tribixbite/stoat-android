import { defineConfig, passthroughImageService } from "astro/config"
import markdownIntegration from "@astropub/md"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
    site: "https://stoatally.app",
    image: {
        service: passthroughImageService(),
    },
    vite: {
        plugins: [tailwindcss()],
    },
    integrations: [markdownIntegration()],
})
