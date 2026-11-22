import asyncio
from typing import Literal
from fastapi.responses import Response
from latex_backend.documents.template_preview import TemplatePreviews


def register(app, store, compiler):
    previews = TemplatePreviews(store.templates, compiler.executable)
    app.state.template_previews = previews

    @app.get('/api/templates/{key}/source')
    async def source(key: str, language: Literal['en', 'vi'] = 'en'):
        return await asyncio.to_thread(previews.source, key, language)

    @app.get('/api/templates/{key}/pdf')
    async def pdf(key: str, language: Literal['en', 'vi'] = 'en'):
        result = await previews.preview(key, language)
        return Response(result['pdf'], media_type='application/pdf', headers={'Content-Disposition': 'inline; filename="template.pdf"'})

    @app.get('/api/templates/{key}/thumbnail')
    async def thumbnail(key: str, language: Literal['en', 'vi'] = 'en'):
        result = await previews.preview(key, language)
        return Response(result['thumbnail'], media_type='image/png')
