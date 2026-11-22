"""Assignment-scoped category selection and exact hundredth-point allocation."""
from decimal import Decimal, ROUND_FLOOR
from latex_backend.storage.store import Problem


def prepare_points(assignment):
    if not assignment.get('scoring'):
        return
    tasks = {str(t) for t in assignment['tasks']}
    selected = assignment.get('assessmentCategories', {})
    if set(selected) - tasks:
        raise Problem('Assessment categories must belong to the selected tasks.')
    for task in tasks:
        profile = assignment['rubricCatalog'][assignment['profiles'][task]]
        available = profile['checks']
        keys = selected.get(task, list(available))
        if not keys or len(keys) != len(set(keys)) or set(keys) - set(available):
            raise Problem('Choose at least one valid assessment category for each task.')
        if any(not available[key] for key in keys):
            raise Problem('Each selected category needs at least one criterion.')
        profile['checks'] = {key: checks for key, checks in available.items() if key in keys}
    point_allocations(assignment)


def point_allocations(assignment):
    entries = []
    for task in sorted(assignment['tasks']):
        profile = assignment['rubricCatalog'][assignment['profiles'][str(task)]]
        for category, checks in profile['checks'].items():
            for check in checks:
                weight = Decimal(str(check.get('weight', 2)))
                if not weight.is_finite() or weight <= 0:
                    raise Problem('Criterion weights must be positive finite numbers.')
                entries.append((str(task), category + ':' + check['id'], weight))
    cents = int(Decimal(str(assignment['scoring']['total'])) * 100)
    if not entries or cents < len(entries):
        raise Problem('The point scale must allow at least 0.01 for every criterion.')
    # Reserve one cent per criterion, then distribute the rest proportionally.
    # Largest remainders make the displayed allocations sum to the exact scale.
    total_weight = sum(e[2] for e in entries)
    raw = [Decimal(cents-len(entries))*entry[2]/total_weight for entry in entries]
    allocated = [int(value.to_integral_value(rounding=ROUND_FLOOR))+1 for value in raw]
    for index in sorted(range(len(entries)), key=lambda i: raw[i] % 1, reverse=True)[:cents-sum(allocated)]:
        allocated[index] += 1
    result = {str(task): {} for task in assignment['tasks']}
    for (task, key, _), value in zip(entries, allocated):
        result[task][key] = value / 100
    return result


def validate_point_grade(assignment, task, grade):
    if grade['profile'] != assignment['profiles'][task]:
        raise Problem('The point rubric is fixed by the submitted assignment snapshot.')
    allowed = point_allocations(assignment)[task]
    scores = grade.get('criterionScores', {})
    if set(scores) - set(allowed):
        raise Problem('Points must belong to the selected assessment criteria.')
    for key, value in scores.items():
        if value is None:
            continue
        number = Decimal(str(value))
        if not number.is_finite() or number < 0 or number > Decimal(str(allowed[key])) or number*100 != (number*100).to_integral_value():
            raise Problem('Criterion points must be within their allocated maximum, in steps of 0.01.')


def point_result(assignment, review):
    allocations = point_allocations(assignment)
    totals = {}
    for task, limits in allocations.items():
        grade = review['tasks'].get(task)
        if not grade:
            raise Problem('Score every selected task before publishing.')
        validate_point_grade(assignment, task, grade)
        scores = grade.get('criterionScores', {})
        if any(scores.get(key) is None for key in limits):
            raise Problem('Score every selected criterion before publishing.')
        totals[task] = round(sum(scores.values()), 2)
    validate_awarded_total(assignment, review)
    calculated = round(sum(totals.values()), 2)
    return {'calculatedScore': calculated, 'awardedTotal': review.get('awardedTotal'),
            'totalScore': review.get('awardedTotal') if review.get('awardedTotal') is not None else calculated, 'maxScore': assignment['scoring']['total'],
            'taskScores': totals, 'pointAllocations': allocations}


def validate_awarded_total(assignment, review):
    value = review.get('awardedTotal')
    if value is None:
        return
    if not assignment.get('scoring'):
        raise Problem('A total override requires a point-scored assignment.')
    number = Decimal(str(value))
    if not number.is_finite() or number < 0 or number > Decimal(str(assignment['scoring']['total'])) or number * 100 != (number * 100).to_integral_value():
        raise Problem('Awarded total must be within the assigned scale, in steps of 0.01.')
