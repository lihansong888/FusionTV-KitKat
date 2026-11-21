"""Compatibility wrapper for the common jsonpath package API used by TVBox spiders.

The old 'jsonpath' distribution has no Android wheel for Chaquopy.
Use the pure-Python jsonpath-ng implementation while preserving:
    jsonpath(data, expression) -> list of matched values or False
"""
from jsonpath_ng.ext import parse


def jsonpath(data, expression):
    try:
        matches = [match.value for match in parse(expression).find(data)]
        return matches if matches else False
    except Exception:
        return False


def parse_expression(expression):
    return parse(expression)
