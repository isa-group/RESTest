package es.us.isa.restest.specification;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.util.List;

/**
 * Class for reading OpenAPI v3 specification files (JSON or YAML)
 */
public class OpenAPISpecification {

	private OpenAPI specification;
	private String path;

	/**
	 * Constructor to deserialize an OpenAPI v3 specification from a file (JSON or YAML).
	 * @param location File location (URL or file path)
	 */
	public OpenAPISpecification(String location) {
		ParseOptions parseOptions = new ParseOptions();
		parseOptions.setResolve(true);
		parseOptions.setResolveFully(true);
		parseOptions.setResolveCombinators(true);

		this.specification = new OpenAPIV3Parser().read(location, null, parseOptions);
		this.path = location;

		if (this.specification == null) {
			System.out.println("Failed to load specification from: " + location);
		} else {
			System.out.println("Specification successfully loaded from: " + location);
			preserveServerQueryParameters();
		}
	}

	public OpenAPI getSpecification() {
		return specification;
	}

	public String getPath() {
		return path;
	}

	public String getTitle(boolean capitalize) {
		String title = "";

		if (specification != null && specification.getInfo() != null) {
			title = specification.getInfo().getTitle().replaceAll("[^\\p{L}\\p{Nd}\\s]+", "").trim();
			title = (capitalize ? title.substring(0, 1).toUpperCase() : title.substring(0, 1).toLowerCase()) +
					(title.length() > 1 ? formatTitle(title.substring(1).split("\\s")) : "");
		}
		return title;
	}

	private static String formatTitle(String[] words) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i == 0) {
				builder.append(words[i]);
			} else {
				builder.append(words[i].substring(0, 1).toUpperCase());
				if (words[i].length() > 1) {
					builder.append(words[i].substring(1));
				}
			}
		}
		return builder.toString();
	}

	/**
	 * Preserves the original query parameters in server URLs if present.
	 */
	private void preserveServerQueryParameters() {
		if (specification != null && specification.getServers() != null) {
			List<Server> servers = specification.getServers();
			for (Server server : servers) {
				String originalUrl = server.getUrl();

				// Update the URL if it contains query parameters
				if (originalUrl != null) {
					server.setUrl(originalUrl);
				}
			}
		}
	}

	public void verifyServerUrls() {
		if (specification == null) {
			System.out.println("The specification could not be loaded.");
			return;
		}

		if (specification.getServers() != null && !specification.getServers().isEmpty()) {
			specification.getServers().forEach(server ->
					System.out.println("Server found: " + server.getUrl())
			);
		} else {
			System.out.println("No servers found in the specification.");
		}
	}
}
