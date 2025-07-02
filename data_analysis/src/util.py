import matplotlib

import os
import json
import re
import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

matplotlib.use("TkAgg")
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
    sns.set(style='whitegrid', font='Times New Roman', font_scale=1.2)
    plt.figure(figsize=(8, 5))
    orientation = "vertical" if dataframe[group_var].nunique() < 10 else "horizontal"
    palette = sns.color_palette('Set2')

    ax = sns.boxplot(x=group_var, y=target_var, data=dataframe, orient=orientation, palette=palette)

    ax.set_title(f'{target_var} by {group_var}', fontsize=16, fontweight='bold', pad=15)
    ax.set_xlabel(group_var, fontsize=14)
    ax.set_ylabel(target_var, fontsize=14)

    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)

    plt.grid(True, linestyle='--', linewidth=0.8, alpha=0.7)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_linewidth(1.2)
    ax.spines['bottom'].set_linewidth(1.2)

    plt.tight_layout()
    plt.savefig(path, dpi=300, bbox_inches='tight')
    plt.close()


def save_mean_ci_plot(df, group_var, target_var, path):
    sns.set(style='whitegrid', font='Times New Roman', font_scale=1.2)
    plt.figure(figsize=(8, 5))
    palette = sns.color_palette('Set2')
    markers = ['o', 's', 'D', '^', 'v', 'P', '*', 'X']

    ax = sns.pointplot(x=group_var, y=target_var, data=df, capsize=0.1,
                       errwidth=1.5, dodge=0.3, markers=markers,
                       linestyles='-', palette=palette, linewidth=1.5)

    ax.set_title(f'{target_var} Mean and 95% CI by {group_var}', fontsize=16, fontweight='bold', pad=15)
    ax.set_xlabel(group_var, fontsize=14)
    ax.set_ylabel(target_var, fontsize=14)

    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)

    plt.grid(True, linestyle='--', linewidth=0.8, alpha=0.7)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_linewidth(1.2)
    ax.spines['bottom'].set_linewidth(1.2)

    plt.tight_layout()
    plt.savefig(path, dpi=300, bbox_inches='tight')
    plt.close()


def run_anova(json_path=None, data=None, target_variable="elecCost", group_variables=None, output_dir=None, use_rpd=True,
              best_known=None):
    if not group_variables or len(group_variables) == 0:
        raise ValueError("group_variables must contain at least one variable.")

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

    # If RPD mode is enabled, convert the target variable to RPD
    if use_rpd:
        if best_known is None:
            # If no best known solution is provided, use the minimum value in the dataset
            best_known = df[target_variable].min()
            print(f"⚙️  Using minimum value {best_known} as best known solution.")
        else:
            # Use the user-provided best known solution
            print(f"⚙️  Using provided best known value: {best_known}")

        # Calculate RPD for each record
        df['RPD'] = ((df[target_variable] - best_known) / best_known) * 100
        target_variable = 'RPD'  # Update target variable to RPD for all subsequent analysis
        print("✅ Converted target variable to RPD.")
        if len(group_variables) == 1:
            _run_one_way_anova(df, base_name, target_variable, group_variables[0], output_dir)
        else:
            _run_multi_way_anova(df, base_name, target_variable, group_variables, output_dir)


def _run_one_way_anova(df, base_name, target_variable, group_var, output_dir):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
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

    tukey_path = os.path.join(output_dir, f"{base_name}_tukey.csv")
    tukey_df = pd.DataFrame(data=tukey._results_table.data[1:], columns=tukey._results_table.data[0])
    tukey_df.to_csv(tukey_path, index=False)
    print("✅ Tukey HSD result saved to:", tukey_path)

    # Save Summary (ANOVA + Tukey HSD)
    summary = (
        f"ANOVA Summary for {base_name}\n"
        f"Timestamp: {timestamp}\n"
        f"Analysis Type: One-Way ANOVA\n"
        f"Target Variable: {target_variable}\n"
        f"Grouping Variable: {group_var}\n"
        f"F-statistic: {f_stat:.4f}\n"
        f"p-value: {p_val:.4f}\n"
        f"Degrees of Freedom: between={df_between}, within={df_within}\n"
        f"Conclusion: {conclusion}\n\n"
        f"Tukey HSD Results:\n"
        f"{tukey.summary()}\n"
    )
    summary_path = os.path.join(output_dir, f"{base_name}_anova_summary.txt")
    save_text(summary_path, summary)
    print("✅ Summary (with Tukey) saved to:", summary_path)

    # Plot Boxplot
    plot_path = os.path.join(output_dir, f"{base_name}_boxplot_{group_var}.png")
    save_plot(df, group_var, target_variable, plot_path)
    print("📈 Boxplot saved to:", plot_path)

    # plot Mean + 95% CI Chart
    mean_ci_path = os.path.join(output_dir, f"{base_name}_mean_ci_{group_var}.png")
    save_mean_ci_plot(df, group_var, target_variable, mean_ci_path)
    print(f"📊 Mean and 95% CI plot saved to: {mean_ci_path}")


def _run_multi_way_anova(df, base_name, target_variable, group_variables, output_dir):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    print("\n--- Multi-Way ANOVA ---")

    for var in group_variables:
        df[var] = df[var].astype('category')

    formula_terms = []
    for r in range(1, len(group_variables) + 1):
        formula_terms += [":".join(comb) for comb in combinations(group_variables, r)]

    formula = f"{target_variable} ~ " + " + ".join(formula_terms)
    print("Model formula:", formula)

    model = ols(formula, data=df).fit()
    anova_table = sm.stats.anova_lm(model, typ=2)

    print("\nANOVA Table:")
    print(anova_table)

    anova_path = os.path.join(output_dir, f"{base_name}_anova_table.csv")
    anova_table.to_csv(anova_path)
    print("📊 ANOVA table saved to:", anova_path)

    # Identify significant factors (excluding Residual)
    significant_factors = anova_table.index[
        (anova_table['PR(>F)'] < 0.05) & (anova_table.index != 'Residual')
        ].tolist()

    tukey_summaries = ""

    if significant_factors:
        print("\n--- Tukey HSD Post-Hoc Tests for Significant Factors ---")
        for factor in significant_factors:
            if ':' not in factor:
                try:
                    print(f"\nPerforming Tukey HSD test for factor: {factor}")
                    tukey = pairwise_tukeyhsd(endog=df[target_variable], groups=df[factor], alpha=0.05)
                    print(tukey.summary())

                    tukey_summaries += f"\nTukey HSD Results for {factor}:\n{tukey.summary()}\n"

                    tukey_path = os.path.join(output_dir, f"{base_name}_tukey_{factor.replace(':', '_')}.csv")
                    tukey_df = pd.DataFrame(data=tukey._results_table.data[1:], columns=tukey._results_table.data[0])
                    tukey_df.to_csv(tukey_path, index=False)
                    print(f"✅ Tukey HSD results saved to: {tukey_path}")
                except Exception as e:
                    print(f"❌ Tukey HSD test for {factor} failed: {e}")
            else:
                print(f"Skipping Tukey test for interaction term '{factor}' (not supported)")
    else:
        print("No significant factors found, skipping Tukey post-hoc tests.")

    summary = (
        f"ANOVA Summary for {base_name}\n"
        f"Timestamp: {timestamp}\n"
        f"Analysis Type: Multi-Way ANOVA\n"
        f"Target Variable: {target_variable}\n"
        f"Grouping Variables: {', '.join(group_variables)}\n\n"
        f"{anova_table}\n"
        f"{tukey_summaries}"
    )

    summary_path = os.path.join(output_dir, f"{base_name}_anova_summary.txt")
    save_text(summary_path, summary)
    print("✅ Summary (with Tukey) saved to:", summary_path)

    # Plot Boxplot and Mean CI
    for var in group_variables:
        boxplot_path = os.path.join(output_dir, f"{base_name}_boxplot_{var}.png")
        mean_ci_path = os.path.join(output_dir, f"{base_name}_mean_ci_{var}.png")
        save_plot(df, var, target_variable, boxplot_path)
        print(f"📈 Boxplot for {var} saved to:", boxplot_path)
        save_mean_ci_plot(df, var, target_variable, mean_ci_path)
        print(f"📊 Mean and 95% CI plot for {var} saved to:", mean_ci_path)


def sanitize_filename(name):
    return re.sub(r'[\\/*?:"<>| ]', '_', str(name))


def plot_rpd_comparison_chart(json_path, x_axis='deadlineFactor', chart_title=None, output_dir=None):
    import os
    import json
    import re
    import pandas as pd
    import seaborn as sns
    import matplotlib.pyplot as plt

    # 1. Load JSON data
    with open(json_path, 'r', encoding='utf-8') as f:
        raw_data = json.load(f)

    # 2. Convert JSON data to DataFrame
    df = pd.DataFrame(raw_data)

    # Handle case where x_axis is a list
    if df[x_axis].apply(lambda x: isinstance(x, list)).any():
        df[x_axis] = df[x_axis].apply(lambda x: '_'.join(str(i) for i in x))

    # 3. Calculate Relative Percentage Deviation (RPD)
    best_known = df['elecCost'].min()
    df['RPD'] = ((df['elecCost'] - best_known) / best_known) * 100

    # 4. Clean 'name' field by removing parentheses and extra spaces
    df['name'] = df['name'].apply(lambda x: re.sub(r'\(.*?\)', '', x).strip())

    # 5. Auto-generate chart title and save path
    base_name = os.path.splitext(os.path.basename(json_path))[0]

    # Function to sanitize filename by removing illegal characters
    def sanitize_filename(name):
        return re.sub(r'[\\/*?:"<>| \[\],]', '_', str(name))

    x_axis_name = sanitize_filename(x_axis)

    # If no title is provided, create a default title
    if not chart_title:
        chart_title = f'{base_name}: RPD vs {x_axis}'

    # If no output directory is specified, use the current directory
    if not output_dir:
        output_dir = '.'  # Default to current directory

    save_file_name = f'{base_name}_{x_axis_name}_comparison_chart.png'
    save_path = os.path.join(output_dir, save_file_name)

    # 6. Style settings (Recommended style for academic papers)
    sns.set(style='whitegrid', font='Times New Roman', font_scale=1.2)
    plt.figure(figsize=(10, 6))
    palette = sns.color_palette('Set2')  # Soft color palette
    markers = ['o', 's', 'D', '^', 'v', 'P', '*', 'X']

    hue_count = df['name'].nunique()

    # 7. Plotting with dodge only if there are multiple algorithms
    plot_params = dict(
        data=df,
        x=x_axis,
        y='RPD',
        hue='name',
        capsize=0.1,
        err_kws={'linewidth': 1.5},
        markers=markers,
        linestyles='-',
        palette=palette,
        linewidth=1.5
    )

    # Only set dodge when there are more than one algorithm
    if hue_count > 1:
        plot_params['dodge'] = 0.3

    # Plot the pointplot with the given parameters
    ax = sns.pointplot(**plot_params)

    # Set title, labels, and font
    ax.set_title(chart_title, fontsize=16, fontweight='bold', pad=15)
    ax.set_xlabel(x_axis, fontsize=14)
    ax.set_ylabel('Relative Percentage Deviation (RPD) %', fontsize=14)

    # Set legend
    if hue_count > 1:
        ax.legend(title='Algorithm', bbox_to_anchor=(1.02, 1), loc='upper left', borderaxespad=0, frameon=False)
    else:
        ax.legend().remove()

    # Set tick label font size
    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)

    # Set grid lines
    plt.grid(True, linestyle='--', linewidth=0.8, alpha=0.7)

    # Set axis line width
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_linewidth(1.2)
    ax.spines['bottom'].set_linewidth(1.2)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    print(f"✅ Chart saved to: {save_path}")
    plt.close()
