# RESTest's Homebrew formula. The tap's copy is written from dist/homebrew/restest.rb in
# https://github.com/isa-group/RESTest by its homebrew.yml workflow when a release is published, and a
# change made to that copy is replaced then: changes go in dist/homebrew/restest.rb.
#
# It installs the release archive as it is, with the Java 21 Homebrew provides, and starts RESTest
# through the archive's own launcher with JAVA_HOME set to that Java, whatever JAVA_HOME the person has.
class Restest < Formula
  desc "Black-box tests for REST APIs, from their OpenAPI document"
  homepage "https://github.com/isa-group/RESTest"
  url "@URL@"
  sha256 "@SHA256@"
  license "Apache-2.0"

  depends_on "openjdk@21"

  def install
    rm "bin/restest.cmd"
    libexec.install Dir["*"]
    (bin/"restest").write_env_script libexec/"bin/restest", Language::Java.java_home_env("21")
  end

  test do
    (testpath/"openapi.yaml").write <<~YAML
      openapi: 3.0.3
      info:
        title: A check of how RESTest starts
        version: '1'
      paths:
        /things:
          get:
            responses:
              '200':
                description: The things
    YAML

    assert_match "RESTest #{version}", shell_output("#{bin}/restest version")
    # Nothing listens on port 1, so nothing can be tested: RESTest answers 3 and still writes its report.
    shell_output("#{bin}/restest run openapi.yaml --url http://127.0.0.1:1 --budget 3s", 3)
    assert_path_exists testpath/"restest-out/report.json"
  end
end
