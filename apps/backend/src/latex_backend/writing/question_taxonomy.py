"""Library filter metadata derived from authorized, immutable rubric snapshots."""
from itertools import chain

NAMES = {'intro':'Introduction', 'body':'Body', 'overview':'Overview',
         'conclusion':'Conclusion', 'closing':'Closing', 'whole':'Whole-paper requirements'}


def facets(rows, catalog=None):
    profiles = {}
    if catalog:
        rows = chain(({'content': {'category': p.get('category','ielts'), 'tasks':[p['task']],
            'profiles':{str(p['task']):key}, 'rubricCatalog':{key:p}}} for key,p in catalog.items()), rows)
    for row in rows:
        content = row.get('content', {})
        category = content.get('category', 'ielts')
        for task in content.get('tasks', []):
            key = content.get('profiles', {}).get(str(task))
            if not key:
                continue
            profile = content.get('rubricCatalog', {}).get(key, {})
            identity = (category, task, key)
            value = profiles.setdefault(identity, {'key':key, 'label':profile.get('label', key),
                'category':category, 'task':task, 'sections':{}})
            for section, checks in profile.get('checks', {}).items():
                value['sections'][section] = profile.get('sectionTitles', {}).get(section) or NAMES.get(section, section)
    return [{**value, 'sections':[{'key':key,'label':label} for key,label in value['sections'].items()]}
            for value in sorted(profiles.values(), key=lambda p:(p['category'],p['task'],p['label'].lower()))]
