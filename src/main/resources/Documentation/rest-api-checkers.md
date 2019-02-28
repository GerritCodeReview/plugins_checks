# @PLUGIN@ - /checkers/ REST API

This page describes the checker-related REST endpoints that are added by the
@PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="checker-endpoints"> Checker Endpoints

### <a id="get-checker"> Get Checker
_'GET /checkers/[\{checker-id\}](#checker-id)'_

Retrieves a checker.

Note that only users with the [Administrate
Checkers](access-control.md#capability_administrateCheckers) global capability
are permitted to retrieve checkers.

#### Request

```
  GET /checkers/e1f530851409c89dbba927efd0fbbaf270bfaeae HTTP/1.0
```

As response a [CheckerInfo](#checker-info) entity is returned that describes the
checker.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  {
    "uuid": "test:my-checker",
    "name": "MyChecker",
    "repository": "examples/Foo",
    "blocking": [],
    "description": "A simple checker.",
    "created_on": "2019-01-31 09:59:32.126000000"
  }
```

### <a id="create-checker"> Create Checker
_'POST /checkers/'_

Creates a new checker.

In the request body the data for the checker must be provided as a
[CheckerInput](#checker-input) entity.

Note that only users with the [Administrate
Checkers](access-control.md#capability_administrateCheckers) global capability
are permitted to create checkers.

#### Request

```
  POST /checkers/ HTTP/1.0
  Content-Type: application/json; charset=UTF-8
  {
    "uuid": "test:my-checker",
    "name": "MyChecker",
    "description": "A simple checker.",
    "repository": "examples/Foo",
  }
```

As response the [CheckerInfo](#checker-info) entity is returned that describes
the created checker.

#### Response

```
  HTTP/1.1 201 Created
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  {
    "uuid": "test:my-checker",
    "name": "MyChecker",
    "description": "A simple checker.",
    "repository": "examples/Foo",
    "created_on": "2019-01-31 09:59:32.126000000",
    "updated_on": "2019-01-31 09:59:32.126000000"
  }
```

### <a id="update-checker"> Update Checker
_'POST /checkers/[\{checker-id\}](#checker-id)'_

Updates a checker.

The new property values must be set in the request body in a
[CheckerInput](#checker-input) entity.

This REST endpoint supports partial updates of the checker property set. Only
properties that are set in the [CheckerInput](#checker-input) entity are
updated. Properties that are not set in the input (or that have `null` as value)
are not touched.

Unsetting properties:

* `uuid`: Cannot be unset or otherwise modified.
* `name`: Can be unset by setting an empty string ("") for it.
* `description`: Can be unset by setting an empty string ("") for it.
* `url`: Can be unset by setting an empty string ("") for it.
* '`repository`: Cannot be unset. Attempting to set it to an empty string ("")
  or a string that is empty after trim is rejected as `400 Bad Request`.
* `status`: Cannot be unset.
* `blocking`: Can be unset by setting an empty list (\[\]) for it.

Note that only users with the [Administrate
Checkers](access-control.md#capability_administrateCheckers) global capability
are permitted to update checkers.

#### Request

```
  POST /checkers/e1f530851409c89dbba927efd0fbbaf270bfaeae HTTP/1.0
  Content-Type: application/json; charset=UTF-8
  {
    "description": "A simple checker."
  }
```

As response the [CheckerInfo](#checker-info) entity is returned that describes
the updated checker.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8
  )]}'
  {
    "uuid": "test:my-checker",
    "name": "MyChecker",
    "description": "A simple checker.",
    "repository": "examples/Foo",
    "status": "ENABLED",
    "created_on": "2019-01-31 09:59:32.126000000",
    "updated_on": "2019-02-01 07:23:44.158000000"
  }
```

## <a id="ids"> IDs

### <a id="checker-id"> \{checker-id\}

The UUID of a checker.

Checker names cannot be used as identifier in the REST API since checker names
may be ambiguous.

UUIDs are of the form `SCHEME ':' ID`. The scheme portion has the format of an
[RFC 3986 scheme](https://tools.ietf.org/html/rfc3986#section-3.1), and is
generally associated with the external system that created the checker, for
example `jenkins`.

The ID portion may be any string that does not contain newlines or null bytes,
and may need to be URL-encoded when passing in a URL. It must not be URL encoded
when passing as a JSON string.

UUIDs must be unique and are immutable after creation.

### <a id="json-entities"> JSON Entities

### <a id="checker-info"> CheckerInfo
The `CheckerInfo` entity describes a checker.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `uuid`          |          | The UUID of the checker.
| `name`          |          | The name of the checker, may not be unique.
| `description`   | optional | The description of the checker.
| `url`           | optional | The URL of the checker.
| `repository`    |          | The (exact) name of the repository for which the checker applies.
| `status`        |          | The status of the checker; one of `ENABLED` or `DISABLED`.
| `blocking`      |          | A list of [conditions](#blocking-conditions) that describe when the checker should block change submission.
| `created_on`    |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the checker was created.
| `updated_on`    |          | The [timestamp](../../../Documentation/rest-api.html#timestamp) of when the checker was last updated.

### <a id="checker-input"> CheckerInput
The `CheckerInput` entity contains information for creating a checker.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `name`          | optional | The name of the checker. Must be specified for checker creation.
| `description`   | optional | The description of the checker.
| `url`           | optional | The URL of the checker.
| `repository`    | optional | The (exact) name of the repository for which the checker applies.
| `status`        | optional | The status of the checker; one of `ENABLED` or `DISABLED`.
| `blocking`      | optional | A list of [conditions](#blocking-conditions) that describe when the checker should block change submission.

## <a id="blocking-conditions"> Blocking Conditions

Blocking conditions allow checkers to specify the conditions under which checks
will block change submission. The conditions configured for a checker are
represented as a list of enum values. When there are multiple blocking
conditions, any one of them is sufficient to block submission; in other words,
conditions are ORed together.

There is currently only a single type of blocking condition:
`STATE_NOT_PASSING`. Intuitively, if a checker sets this blocking condition,
that means that users need to wait for all checks, for example build and test,
to pass before submitting the change. In other words, we might say the checker
is _required_.

Technically, `STATE_NOT_PASSING` requires the combined check state on the change
to be either `SUCCESSFUL` or `NOT_RELEVANT`.

---

Part of [Gerrit Code Review](../../../Documentation/index.html)
