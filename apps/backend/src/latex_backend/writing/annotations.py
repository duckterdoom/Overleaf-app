"""Immutable submission anchors use UTF-16 offsets, matching browser Range text."""
from latex_backend.storage.store import Problem


def validate_annotations(text, annotations, allowed_categories, previous, current, timestamp):
    try:
        encoded = text.encode('utf-16-le')
    except UnicodeError:
        raise Problem('The submitted text contains incomplete Unicode characters.') from None
    seen = set()
    prior = {a['id']:a for a in previous}
    for note in annotations:
        start,end = note['start'],note['end']
        if note['id'] in seen or end <= start or end*2 > len(encoded):
            raise Problem('Comment anchors must be unique and inside the submitted answer.')
        seen.add(note['id'])
        try:
            quote = encoded[start*2:end*2].decode('utf-16-le')
        except UnicodeError:
            raise Problem('Select complete characters when attaching a comment.') from None
        if quote != note['quote']:
            raise Problem('The selected comment text does not match the immutable submission.')
        if note['category'] and note['category'] not in allowed_categories:
            raise Problem('Comment categories must belong to the assigned assessment criteria.')
        if note['kind']=='comment' and not note['comment'].strip():
            raise Problem('Enter a comment for the selected text.')
        if note['kind']=='replace' and not note['replacement'].strip():
            raise Problem('Enter a suggested correction, or use strikethrough for a deletion.')
        original = prior.get(note['id'], {})
        note['authorName'] = original.get('authorName') or current['name']
        note['createdAt'] = original.get('createdAt') or timestamp
