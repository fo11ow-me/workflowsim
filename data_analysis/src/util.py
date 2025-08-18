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


def load_data(path):
    data = []

    if path.endswith('.jsonl'):
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    data.append(json.loads(line))
    elif path.endswith('.json'):
        with open(path, 'r', encoding='utf-8') as f:
            raw = json.load(f)
            if isinstance(raw, list):
                data = raw
            else:
                data = [raw]
    elif path.endswith('.csv'):
        df = pd.read_csv(path)
        data = df.to_dict(orient='records')
    else:
        raise ValueError(f"Unsupported file format: {path}")

    print(f"✅ Loaded {len(data)} records from {path}")
    return data


def save_text(path, content):
    with open(path, "w", encoding="utf-8") as f:
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


def anova(path=None, data=None, target_variable="elecCost", group_variables=None, output_dir=None,
          use_rpd=True,
          best_known=None):
    if not group_variables or len(group_variables) == 0:
        raise ValueError("group_variables must contain at least one variable.")

    if data is not None:
        df = pd.DataFrame(data)
        base_name = "provided_data"
    elif path and os.path.exists(path):
        raw_data = load_data(path)
        df = pd.DataFrame(raw_data)
        base_name = os.path.splitext(os.path.basename(path))[0]
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
            _one_way_anova(df, base_name, target_variable, group_variables[0], output_dir)
        else:
            _multi_way_anova(df, base_name, target_variable, group_variables, output_dir)


def _one_way_anova(df, base_name, target_variable, group_variable, output_dir):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    groups = df.groupby(group_variable)[target_variable].apply(list)

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
    tukey = pairwise_tukeyhsd(endog=df[target_variable], groups=df[group_variable], alpha=0.05)
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
        f"Grouping Variable: {group_variable}\n"
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
    plot_path = os.path.join(output_dir, f"{base_name}_boxplot_{group_variable}.png")
    save_plot(df, group_variable, target_variable, plot_path)
    print("📈 Boxplot saved to:", plot_path)

    # plot Mean + 95% CI Chart
    mean_ci_path = os.path.join(output_dir, f"{base_name}_mean_ci_{group_variable}.png")
    save_mean_ci_plot(df, group_variable, target_variable, mean_ci_path)
    print(f"📊 Mean and 95% CI plot saved to: {mean_ci_path}")


def _multi_way_anova(df, base_name, target_variable, group_variables, output_dir):
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


def plot_comparison_chart(path, x_axis='deadlineFactor', y_axis='elecCost',
                          chart_title=None, output_dir=None, use_rpd=True):
    # 1. data
    raw_data = load_data(path)

    # 2. Convert to DataFrame
    df = pd.DataFrame(raw_data)

    # 3. Handle list-type x_axis
    if df[x_axis].apply(lambda x: isinstance(x, list)).any():
        df[x_axis] = df[x_axis].apply(lambda x: '_'.join(str(i) for i in x))

    # 4. Set y-axis variable (either elecCost or RPD)
    if use_rpd:
        best_known = df[y_axis].min()
        df['RPD'] = ((df[y_axis] - best_known) / best_known) * 100
        plot_y = 'RPD'
    else:
        plot_y = y_axis

    # 5. Clean algorithm name
    df['name'] = df['name'].apply(lambda x: re.sub(r'\(.*?\)', '', x).strip())

    # 6. File path & title
    base_name = os.path.splitext(os.path.basename(path))[0]
    x_axis_name = sanitize_filename(x_axis)
    y_axis_name = sanitize_filename(plot_y)

    if not chart_title:
        chart_title = f'{base_name}: {plot_y} vs {x_axis}'

    if not output_dir:
        output_dir = '.'

    save_file_name = f'{base_name}_{x_axis_name}_vs_{y_axis_name}_chart.png'
    save_path = os.path.join(output_dir, save_file_name)

    # 7. Plot
    sns.set(style='whitegrid', font='Times New Roman', font_scale=1.2)
    plt.figure(figsize=(10, 6))
    palette = sns.color_palette('Set2')
    markers = ['o', 's', 'D', '^', 'v', 'P', '*', 'X']
    hue_count = df['name'].nunique()

    plot_params = dict(
        data=df,
        x=x_axis,
        y=plot_y,
        hue='name',
        capsize=0.1,
        err_kws={'linewidth': 1.5},
        markers=markers,
        linestyles='-',
        palette=palette,
        linewidth=1.5
    )
    if hue_count > 1:
        plot_params['dodge'] = 0.3

    ax = sns.pointplot(**plot_params)

    # 8. Customize appearance
    ax.set_title(chart_title, fontsize=16, fontweight='bold', pad=15)
    ax.set_xlabel(x_axis, fontsize=14)
    ylabel = 'Relative Percentage Deviation (RPD) %' if use_rpd else plot_y
    ax.set_ylabel(ylabel, fontsize=14)

    if hue_count > 1:
        ax.legend(title='Algorithm', bbox_to_anchor=(1.02, 1), loc='upper left', borderaxespad=0, frameon=False)
    else:
        ax.legend().remove()

    plt.xticks(fontsize=12)
    plt.yticks(fontsize=12)
    plt.grid(True, linestyle='--', linewidth=0.8, alpha=0.7)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_linewidth(1.2)
    ax.spines['bottom'].set_linewidth(1.2)

    plt.tight_layout()
    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    print(f"✅ Chart saved to: {save_path}")
    plt.close()
