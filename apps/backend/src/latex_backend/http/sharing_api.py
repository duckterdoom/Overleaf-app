from pydantic import BaseModel, Field
from fastapi import Request
from typing import Literal

from latex_backend.storage.store import Problem


class ShareSettings(BaseModel):
    mode: str
    permission: str = 'viewer'
    rotate: bool = False
    workspaceRole: Literal['tutor', 'student'] | None = None
    ownerRole: Literal['tutor', 'student'] | None = None


class Invite(BaseModel):
    emails: list[str] = Field(min_length=1, max_length=20)
    permission: str = 'viewer'
    workspaceRole: Literal['tutor', 'student'] = 'student'


class Accept(BaseModel):
    token: str = Field(min_length=20, max_length=256)


class Member(BaseModel):
    permission: Literal['viewer', 'editor'] | None = None
    workspaceRole: Literal['tutor', 'student'] | None = None


def register_sharing(gateway):
    app, sharing = gateway.auth, gateway.sharing

    def changed(project, result):
        gateway.presence.access_changed(project)
        return result

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
            result.append({**project, 'permission': 'owner', 'shared': False, 'ownerName': principal['name'], 'workspaceRole': sharing.workspace_role(project['id'], principal['id'])})
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
        return changed(project, sharing.configure(project, principal['id'], data.mode, data.permission, data.rotate, data.workspaceRole, data.ownerRole))

    @app.post('/api/projects/{project}/sharing/invitations')
    async def invite(project: str, data: Invite, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return changed(project, sharing.invite(project, principal['id'], data.emails, data.permission, data.workspaceRole))

    @app.patch('/api/projects/{project}/sharing/members/{identifier}')
    async def update_member(project: str, identifier: str, data: Member, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        if data.permission is None and data.workspaceRole is None:
            raise Problem('Chọn quyền hoặc vai trò cần thay đổi.')
        return changed(project, sharing.member(project, principal['id'], identifier, data.permission, data.workspaceRole))

    @app.delete('/api/projects/{project}/sharing/members/{identifier}')
    async def remove_member(project: str, identifier: str, request: Request):
        principal = user(request)
        await gateway.project_workspace(project, principal)
        return changed(project, sharing.member(project, principal['id'], identifier))

    @app.post('/api/shares/accept')
    async def accept(data: Accept, request: Request):
        principal = user(request)
        identifier = sharing.accept(data.token, principal)
        gateway.presence.access_changed(identifier)
        return await gateway.project_info(identifier, principal)
