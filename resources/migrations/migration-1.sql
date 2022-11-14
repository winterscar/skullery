CREATE TABLE IF NOT EXISTS Ingredients (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR,
  unit_name VARCHAR,
  unit_name_plural VARCHAR,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS Conversions (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ingredient_id INT,
  FOREIGN KEY (ingredient_id) REFERENCES Ingredients(id),
  convert_to ENUM('MASS', 'VOLUME', 'LENGTH', 'UNIT'),
  convert_from ENUM('MASS', 'VOLUME', 'LENGTH', 'UNIT'),
  to_note VARCHAR,
  from_note VARCHAR,
  multiplier DOUBLE PRECISION,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS Recipes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS RecipeIngredients (
  id INT AUTO_INCREMENT PRIMARY KEY,
  ingredient_id INT,
  recipe_id INT,
  FOREIGN KEY (ingredient_id) REFERENCES Ingredients(id),
  FOREIGN KEY (recipe_id) REFERENCES Recipes(id),
  quantity DOUBLE PRECISION,
  unit ENUM('MASS', 'VOLUME', 'LENGTH', 'UNIT')
);

CREATE TABLE IF NOT EXISTS Equipment (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS RecipeEquipment (
  recipe_id INT,
  equipment_id INT,
  FOREIGN KEY (recipe_id) REFERENCES Recipes(id),
  FOREIGN KEY (equipment_id) REFERENCES Equipment(id)
);

CREATE TABLE IF NOT EXISTS Steps (
  id INT AUTO_INCREMENT PRIMARY KEY,
  recipe_id INT,
  FOREIGN KEY (recipe_id) REFERENCES Recipes(id),
  name VARCHAR(255),
  body CHARACTER LARGE OBJECT,
  step_order INT,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE GENERATED ALWAYS AS CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS StepIngredients (
  recipe_ingredient_id INT,
  step_id INT,
  FOREIGN KEY (recipe_ingredient_id) REFERENCES RecipeIngredients(id),
  FOREIGN KEY (step_id) REFERENCES Steps(id),
  quantity DOUBLE PRECISION
);

CREATE TABLE DatabaseVersion (
  version INT,
  migrated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
)