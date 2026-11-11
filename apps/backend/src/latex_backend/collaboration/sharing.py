"""MongoDB-backed project ACLs and revocable invitation/link grants."""
import secrets
from latex_backend.storage.state import State
from latex_backend.storage.store import Problem


class Sharing:
    def __init__(self, accounts):
        self.accounts, self.db = accounts, accounts.db

    def register(self, project, owner):
        self.db.shared_projects.update_one({'project': project}, {'$setOnInsert': {
            '_id': project, 'project': project, 'owner': owner, 'mode': 'private', 'permission': 'viewer'}}, upsert=True)

    def owner(self, project):
        row = self.db.shared_projects.find_one({'project': project})
        return row['owner'] if row else None

    def access(self, project, user_id):
        row = self.db.shared_projects.find_one({'project': project})
        if not row:
            return None
        if row['owner'] == user_id:
            return 'owner'
        member = self.db.invitations.find_one({'project': project, 'user_id': user_id})
        visitor = self.db.link_visitors.find_one({'project': project, 'user_id': user_id})
        link_role = row['permission'] if row['mode'] == 'link' and visitor and visitor['token'] == row.get('token') else None
        if (member and member['permission'] == 'editor') or link_role == 'editor':
            return 'editor'
        return member['permission'] if member else link_role

    def projects_for(self, user_id):
        identifiers = set(self.db.invitations.distinct('project', {'user_id': user_id})) | set(self.db.link_visitors.distinct('project', {'user_id': user_id}))
        return [identifier for identifier in identifiers if self.access(identifier, user_id)]

    def info(self, project, user_id):
        permission = self.access(project, user_id)
        if not permission:
            raise Problem('Không tìm thấy dự án hoặc bạn không có quyền truy cập.', 404)
        row = self.db.shared_projects.find_one({'project': project})
        owner = self.accounts.user(row['owner'])
        result = {'permission': permission, 'owner': {'name': owner['name'], 'email': owner['email']},
            'mode': row['mode'], 'linkPermission': row['permission'], 'linkToken': None, 'members': []}
        if permission == 'owner':
            result['linkToken'] = row.get('token')
            result['members'] = [{'id': item['_id'], 'email': item['email'], 'permission': item['permission'],
                'status': 'accepted' if item.get('user_id') else 'pending', 'inviteToken': item['token']}
                for item in self.db.invitations.find({'project': project}).sort('email', 1)]
        return result

    def require_owner(self, project, user_id):
        if self.access(project, user_id) != 'owner':
            raise Problem('Chỉ chủ sở hữu được quản lý chia sẻ.', 403)

    def configure(self, project, user_id, mode, permission, rotate=False):
        self.require_owner(project, user_id)
        if mode not in {'private', 'link'} or permission not in {'viewer', 'editor'}:
            raise Problem('Thiết lập chia sẻ không hợp lệ.')
        row = self.db.shared_projects.find_one({'project': project})
        values = {'mode': mode, 'permission': permission}
        update = {'$set': values}
        if mode == 'link':
            values['token'] = secrets.token_urlsafe(32) if rotate or not row.get('token') else row['token']
        else:
            update['$unset'] = {'token': ''}
        self.db.shared_projects.update_one({'project': project, 'owner': user_id}, update)
        return self.info(project, user_id)

    def invite(self, project, user_id, emails, permission):
        self.require_owner(project, user_id)
        if permission not in {'viewer', 'editor'} or not 1 <= len(emails) <= 20:
            raise Problem('Chọn quyền hợp lệ và tối đa 20 email mỗi lần.')
        normalized = list(dict.fromkeys(State.email(value) for value in emails))
        if any(any(char.isspace() for char in value) or ',' in value or ';' in value for value in normalized):
            raise Problem('Nhập các địa chỉ email hợp lệ.')
        if self.accounts.user(user_id)['email'] in normalized:
            raise Problem('Bạn đã là chủ sở hữu của dự án này.')
        for email in normalized:
            self.db.invitations.update_one({'project': project, 'email': email}, {
                '$set': {'permission': permission}, '$setOnInsert': {'_id': secrets.token_hex(16),
                'project': project, 'email': email, 'token': secrets.token_urlsafe(32)}}, upsert=True)
        return self.info(project, user_id)

    def member(self, project, user_id, identifier, permission=None):
        self.require_owner(project, user_id)
        query = {'project': project, '_id': identifier}
        if permission is None:
            found = self.db.invitations.delete_one(query).deleted_count
        elif permission in {'viewer', 'editor'}:
            found = self.db.invitations.update_one(query, {'$set': {'permission': permission}}).matched_count
        else:
            raise Problem('Quyền không hợp lệ.')
        if not found:
            raise Problem('Không tìm thấy thành viên.', 404)
        return self.info(project, user_id)

    def accept(self, token, user):
        invite = self.db.invitations.find_one({'token': token})
        if invite:
            if invite['email'] != user['email'] or invite.get('user_id') not in {None, user['id']}:
                raise Problem('Hãy đăng nhập đúng tài khoản email được mời.', 403)
            updated = self.db.invitations.update_one({'_id': invite['_id'], 'token': token,
                '$or': [{'user_id': None}, {'user_id': user['id']}]}, {'$set': {'user_id': user['id']}})
            if not updated.matched_count:
                raise Problem('Lời mời đã bị thu hồi.', 404)
            return invite['project']
        link = self.db.shared_projects.find_one({'token': token, 'mode': 'link'})
        if not link:
            raise Problem('Liên kết không hợp lệ hoặc đã bị thu hồi.', 404)
        self.db.link_visitors.update_one({'project': link['project'], 'user_id': user['id']}, {'$set': {'token': token}}, upsert=True)
        return link['project']
