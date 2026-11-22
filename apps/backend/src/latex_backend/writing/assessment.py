"""Public assessment groups, preserving stored report snapshots as a Research subtype."""


def assessment_group(category):
    return 'ielts' if not category or category == 'ielts' else 'research'


def category_matches(actual, requested):
    if not requested:
        return True
    if requested == 'research':
        return actual in {'report', 'research'}
    return (actual or 'ielts') == requested
