package io.github.woxlo1.deploymc.transport;

/** A request-shape failure with a stable catalog code and HTTP status. */
final class RequestProblem extends Exception {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final int status;

  private RequestProblem(String code, int status) {
    super(code);
    this.code = code;
    this.status = status;
  }

  static RequestProblem invalid() {
    return new RequestProblem("request.invalid", 400);
  }

  static RequestProblem tooLarge() {
    return new RequestProblem("request.too-large", 413);
  }

  static RequestProblem unsupportedMediaType() {
    return new RequestProblem("request.unsupported-media-type", 415);
  }

  static RequestProblem invalidCursor() {
    return new RequestProblem("request.invalid-cursor", 400);
  }

  static RequestProblem preconditionRequired() {
    return new RequestProblem("request.precondition-required", 428);
  }

  static RequestProblem invalidRange() {
    return new RequestProblem("files.invalid-range", 416);
  }

  String code() {
    return code;
  }

  int status() {
    return status;
  }
}
