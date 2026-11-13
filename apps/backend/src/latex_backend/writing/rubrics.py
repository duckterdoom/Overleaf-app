"""Validation and scoring shared by assignment and reusable question snapshots."""
import copy
import re

from latex_backend.storage.store import Problem


def validate_catalog(catalog):
    for key, profile in catalog.items():
        if not re.fullmatch(r'[a-zA-Z0-9_-]{1,40}', key):
            raise Problem('Invalid rubric identifier.')
        category = profile.get('category', 'ielts')
        if category == 'ielts':
            if profile['family'] not in {'academic', 'general', 'essay'}:
                raise Problem('Invalid IELTS rubric family.')
        elif profile['family'] != category or profile['task'] != 1:
            raise Problem('Rubric category and task do not match.')
        for section, checks in profile['checks'].items():
            if not re.fullmatch(r'[a-zA-Z0-9_-]{1,40}', section):
                raise Problem('Invalid rubric section identifier.')
            if len(checks) > 40 or len({check['id'] for check in checks}) != len(checks):
                raise Problem('Use at most 40 criteria per section with unique identifiers.')
            for check in checks:
                if not check['codes'] or len(set(check['codes'])) != len(check['codes']):
                    raise Problem('Each criterion needs unique assessment codes.')
                if category == 'ielts' and set(check['codes']) - {'TA', 'TR', 'CC', 'LR', 'GRA'}:
                    raise Problem('Invalid IELTS assessment code.')


def assignment_catalog(catalog, scope):
    validate_catalog(catalog)
    value = copy.deepcopy(catalog)
    if scope != 'whole':
        for profile in value.values():
            profile['checks'] = {key: checks for key, checks in profile['checks'].items() if key == scope}
    return value


def uses_bands(assignment):
    return assignment.get('category', 'ielts') == 'ielts' and assignment.get('writingScope', 'whole') == 'whole'


def checklist_result(assignment, review):
    """One vote per criterion; repeated evidence uses the weakest recorded result."""
    values = {}
    for task, grade in review['tasks'].items():
        profile = assignment['rubricCatalog'][grade['profile']]
        for section in grade['sections']:
            for check in profile['checks'].get(section['kind'], []):
                state = section['checks'].get(check['id'], 'unreviewed')
                if state == 'na':
                    continue
                if state == 'unreviewed':
                    raise Problem('Review every criterion in the selected sections before publishing.')
                key = (task, section['kind'], check['id'])
                values[key] = min(values.get(key, 1), {'met': 1, 'partial': .5, 'missing': 0}[state])
    if not values:
        raise Problem('Review at least one criterion before publishing this section exercise.')
    return {'scorePercent': round(100 * sum(values.values()) / len(values), 1), 'criteriaCount': len(values)}
