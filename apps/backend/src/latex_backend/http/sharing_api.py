from pydantic import BaseModel, Field
from fastapi import Request

from latex_backend.storage.store import Problem


class ShareSettings(BaseModel):
    mode: str
    permission: str = 'viewer'
    rotate: bool = False


class Invite(BaseModel):
    emails: list[str] = Field(min_length=1, max_length=20)
    permission: str = 'viewer'


class Accept(BaseModel):
    token: str = Field(min_length=20, max_length=256)


class Member(BaseModel):
    permission: str


def register_sharing(gateway):
    app, sharing = gateway.auth, gateway.sharing

    def user(request):
        principal = gateway.principal(request)
        if not principal['role']:
            raise Problem('Hoàn tất bước chọn vai trò trước khi mở dự án.', 403)
        return principal

    @app.get('/api/projects')
    async def projects(request: Request):
        principal = gateway.accounts.resolve(request.cookies.get('latex_account', ''))
        if not principal or not principal['role']:
            return []
        _, store = await gateway.workspace(principal)
        result = []
        for project in store.list():
            sharing.register(project['id'], principal['id'])
            result.append({**project, 'permission': 'owner', 'shared': False, 'ownerName': principal['name']})
        for identifier in sharing.projects_for(principal['id']):
            if sharing.owner(identifier) == principal['id']:
                continue
            try:
                result.append(await gateway.project_info(identifier, principal))
            except Problem:
                pass  # Deleted projects never appear as stale shared entries.
        return result

    @app.get('/api/projects/{project}/sharing')
    async def get_sharing(project: str, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return sharing.info(project, principal['id'])

    @app.patch('/api/projects/{project}/sharing')
    async def configure(project: str, data: ShareSettings, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return sharing.configure(project, principal['id'], data.mode, data.permission, data.rotate)

    @app.post('/api/projects/{project}/sharing/invitations')
    async def invite(project: str, data: Invite, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return sharing.invite(project, principal['id'], data.emails, data.permission)

    @app.patch('/api/projects/{project}/sharing/members/{identifier}')
    async def update_member(project: str, identifier: str, data: Member, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return sharing.member(project, principal['id'], identifier, data.permission)

    @app.delete('/api/projects/{project}/sharing/members/{identifier}')
    async def remove_member(project: str, identifier: str, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return sharing.member(project, principal['id'], identifier)

    @app.post('/api/shares/accept')
    async def accept(data: Accept, request: Request):
        principal = user(request)
        identifier = sharing.accept(data.token, principal)
        return await gateway.project_info(identifier, principal)
