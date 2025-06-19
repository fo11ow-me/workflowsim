import pandas as pd
import json

base_path = "../data/experiment/"

# Read JSON data
with open(base_path + "Demo10.json", encoding="utf-8") as f:
    data = json.load(f)

# Convert to DataFrame
df = pd.DataFrame(data)

# Define grouping variable and target variable
group_var = "jobSequenceStrategy"
target_var = "elecCost"

# Group the target variable by the grouping variable
grouped = df.groupby(group_var)[target_var].apply(list)

# Convert to DataFrame and transpose so each column represents a group
group_df = pd.DataFrame(dict(grouped))

# Export to CSV
output_path = base_path + "grouped_by_strategy.csv"
group_df.to_csv(output_path, index=False)
print(f"✅ Export successful: {output_path}")
