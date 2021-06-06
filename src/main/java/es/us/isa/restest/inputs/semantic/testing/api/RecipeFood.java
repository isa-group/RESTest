package es.us.isa.restest.inputs.semantic.testing.api;

import java.io.IOException;

import static es.us.isa.restest.inputs.semantic.testing.MainTesting.printResponse;

public class RecipeFood {

    private static final String baseUri = "https://spoonacular-recipe-food-nutrition-v1.p.rapidapi.com";

    // /food/images/classify (Image Classification)
    // imageUrl
    public static void recipeFood_imageClassification_imageUrl(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/images/classify?imageUrl=" + semanticInput;
        printResponse(url);
    }

    // /food/images/analyze (Image Analysis)
    // imageUrl
    public static void recipeFood_imageAnalysis_imageUrl(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/images/analyze?imageUrl=" + semanticInput;
        printResponse(url);
    }

    // /recipes/guessNutrition (Guess Nutrition by dishname)
    // title
    public static void recipeFood_guessNutritionByDishname_title(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/recipes/guessNutrition?title=" + semanticInput;
        printResponse(url);
    }

    // /recipes/extract (Extract recipe from website)
    // url
    public static void recipeFood_extractRecipeFromWebsite_url(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/recipes/extract?url=" + semanticInput;
        printResponse(url);
    }

    // /food/wine/description (Get wine description)
    // wine
    public static void recipeFood_getWineDescription_wine(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/wine/description?wine=" + semanticInput;
        printResponse(url);
    }

    // /food/wine/pairing (Get wine pairing)
    // food
    public static void recipeFood_getWinePairing_food(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/wine/pairing?food=" + semanticInput;
        printResponse(url);
    }

    // /food/products/upc/{upc}/comparable (Get comparable products)
    // upc
    public static void recipeFood_getComparableProducts_upc(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/products/upc/" + semanticInput + "/comparable";
        printResponse(url);
    }

    // /food/ingredients/substitutes (Get ingredients substitute)
    // ingredientName
    public static void recipeFood_getIngredientsSubstitute_ingredientName(String semanticInput, String apiKey, String host) throws IOException {
        String url = baseUri + "/food/ingredients/substitutes?ingredientName=" + semanticInput;
        printResponse(url);
    }

}
