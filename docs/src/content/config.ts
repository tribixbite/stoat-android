import { defineCollection, z } from "astro:content"

export const collections = {
    changelogs: defineCollection({
        schema: z.object({
            version: z.object({
                code: z.number(),
                name: z.string(),
                title: z.string(),
            }),
            date: z.object({
                publish: z.date(),
            }),
            summary: z.string(),
        }),
    }),
}
