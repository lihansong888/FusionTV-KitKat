"""Small compatibility layer for spiders which import ujson.

Chaquopy does not provide a compatible ujson wheel for this Android target.
The standard-library json module covers the common ujson API used by TVBox
spiders, without introducing a native dependency.
"""
import json as _json

JSONDecodeError = _json.JSONDecodeError


def loads(value, *args, **kwargs):
    return _json.loads(value, *args, **kwargs)


def dumps(value, *args, **kwargs):
    kwargs.pop("escape_forward_slashes", None)
    return _json.dumps(value, *args, **kwargs)


def load(fp, *args, **kwargs):
    return _json.load(fp, *args, **kwargs)


def dump(value, fp, *args, **kwargs):
    kwargs.pop("escape_forward_slashes", None)
    return _json.dump(value, fp, *args, **kwargs)


def encode(value, *args, **kwargs):
    return dumps(value, *args, **kwargs)


def decode(value, *args, **kwargs):
    return loads(value, *args, **kwargs)
