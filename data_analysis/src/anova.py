import json
import os
import pandas as pd
import matplotlib

matplotlib.use("TkAgg")
import matplotlib.pyplot as plt
import seaborn as sns
import statsmodels.api as sm
from statsmodels.formula.api import ols
from statsmodels.stats.multicomp import pairwise_tukeyhsd
from scipy.stats import f_oneway
from datetime import datetime
from itertools import combinations


def load_json_data(json_path):
    with open(json_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_text(filepath, content):
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)


def save_plot(dataframe, group_var, target_var, path):
    plt.figure(figsize=(8, 5))
    orientation = "v" if dataframe[group_var].nunique() < 10 else "h"
    sns.boxplot(x=group_var, y=target_var, data=dataframe, orient=orientation)
    plt.title(f"{target_var} by {group_var}")
    plt.savefig(path, dpi=300)
    plt.close()


def run(json_path=None,
        data=None,
        target_variable="elecCost",
        group_variables=None,
        output_dir=None):
    """
    Run one-way or multi-way ANOVA, save results, plot and export boxplots.

    Parameters:
        json_path (str): Path to JSON file.
        data (list or DataFrame): Input data.
        target_variable (str): Dependent variable.
        group_variables (list[str]): Grouping variable(s).
        output_dir (str): Folder to store results.
    """
    if not group_variables or len(group_variables) == 0:
        raise ValueError("group_variables must contain at least one variable.")

    # Load data
    if data is not None:
        df = pd.DataFrame(data)
        base_name = "provided_data"
    elif json_path and os.path.exists(json_path):
        raw_data = load_json_data(json_path)
        df = pd.DataFrame(raw_data)
        base_name = os.path.splitext(os.path.basename(json_path))[0]
    else:
        raise ValueError("Either json_path or data must be provided.")

    os.makedirs(output_dir, exist_ok=True)
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    if len(group_variables) == 1:
        # One-Way ANOVA
        group_var = group_variables[0]
        groups = df.groupby(group_var)[target_variable].apply(list)

        print("\n--- One-Way ANOVA ---")
        for name, values in groups.items():
            print(f"{name}: n={len(values)}")

        f_stat, p_val = f_oneway(*groups)
        df_total = len(df) - 1
        df_between = len(groups) - 1
        df_within = df_total - df_between
        conclusion = "✅ Significant difference." if p_val < 0.05 else "❌ No significant difference."

        print(f"\nF-statistic: {f_stat:.4f}")
        print(f"p-value: {p_val:.4f}")
        print(f"Degrees of Freedom: between={df_between}, within={df_within}")
        print("Conclusion:", conclusion)

        print("\n--- Tukey HSD Post-Hoc Test ---")
        tukey = pairwise_tukeyhsd(endog=df[target_variable], groups=df[group_var], alpha=0.05)
        print(tukey.summary())

        # Save Tukey result
        tukey_path = os.path.join(output_dir, f"{base_name}_tukey.csv")
        tukey_df = pd.DataFrame(data=tukey._results_table.data[1:], columns=tukey._results_table.data[0])
        tukey_df.to_csv(tukey_path, index=False)
        print("✅ Tukey HSD result saved to:", tukey_path)

        # Save ANOVA summary
        summary = (
            f"ANOVA Summary for {base_name}\n"
            f"Timestamp: {timestamp}\n"
            f"Analysis Type: One-Way ANOVA\n"
            f"Target Variable: {target_variable}\n"
            f"Grouping Variable: {group_var}\n"
            f"F-statistic: {f_stat:.4f}\n"
            f"p-value: {p_val:.4f}\n"
            f"Degrees of Freedom: between={df_between}, within={df_within}\n"
            f"Conclusion: {conclusion}\n"
        )
        summary_path = os.path.join(output_dir, f"{base_name}_anova_summary.txt")
        save_text(summary_path, summary)
        print("✅ Summary saved to:", summary_path)

        # Plot boxplot
        plot_path = os.path.join(output_dir, f"{base_name}_boxplot_{group_var}.png")
        save_plot(df, group_var, target_variable, plot_path)
        print("📈 Boxplot saved to:", plot_path)

    else:
        # Multi-Way ANOVA
        print("\n--- Multi-Way ANOVA ---")

        # Ensure all group variables are treated as categorical
        for var in group_variables:
            df[var] = df[var].astype('category')

        # Generate all interaction terms up to N-way
        formula_terms = []
        n_factors = len(group_variables)
        for r in range(1, n_factors + 1):  # 1st-order to Nth-order
            formula_terms += [":".join(comb) for comb in combinations(group_variables, r)]

        formula = f"{target_variable} ~ " + " + ".join(formula_terms)
        print("Model formula:", formula)

        model = ols(formula, data=df).fit()
        anova_table = sm.stats.anova_lm(model, typ=2)

        print("\nANOVA Table:")
        print(anova_table)

        # Save the result table
        anova_path = os.path.join(output_dir, f"{base_name}_anova_table.csv")
        anova_table.to_csv(anova_path)
        print("📊 ANOVA table saved to:", anova_path)

        summary = (
            f"ANOVA Summary for {base_name}\n"
            f"Timestamp: {timestamp}\n"
            f"Analysis Type: Multi-Way ANOVA\n"
            f"Target Variable: {target_variable}\n"
            f"Grouping Variables: {', '.join(group_variables)}\n\n"
            f"{anova_table}\n"
        )
        summary_path = os.path.join(output_dir, f"{base_name}_anova_summary.txt")
        save_text(summary_path, summary)
        print("✅ Summary saved to:", summary_path)

        # Plot box plots for each individual factor
        for var in group_variables:
            plot_path = os.path.join(output_dir, f"{base_name}_boxplot_{var}.png")
            save_plot(df, var, target_variable, plot_path)
            print(f"📈 Boxplot for {var} saved to:", plot_path)
